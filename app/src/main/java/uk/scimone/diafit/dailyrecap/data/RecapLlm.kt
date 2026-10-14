package uk.scimone.diafit.dailyrecap.data

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import uk.scimone.diafit.core.data.local.LlmLogDao
import uk.scimone.diafit.core.data.networking.AiHttpException
import uk.scimone.diafit.core.data.networking.OpenAiApi
import uk.scimone.diafit.core.domain.model.LlmRequestLogEntity
import uk.scimone.diafit.core.domain.util.networking.NetworkError
import uk.scimone.diafit.core.domain.util.networking.Result
import uk.scimone.diafit.dailyrecap.domain.RecapInput
import uk.scimone.diafit.settings.domain.model.AiConnection

/** What the model wrote, before it is joined with the chart data. Times are "HH:mm" or "". */
data class RecapText(
    val headline: String,
    val summary: String,
    val mood: String,
    val wentWell: List<Pair<String, String>>,
    val wentWrong: List<Pair<String, String>>,
    val doToday: List<String>
)

/** Asks the LLM to write the recap from a [RecapInput]; every request is logged like the other LLM features. */
class RecapLlm(private val api: OpenAiApi, private val llmLog: LlmLogDao) {
    private val json = Json { encodeDefaults = false; explicitNulls = false }

    fun inputJson(input: RecapInput): String = json.encodeToString(RecapInput.serializer(), input)

    suspend fun write(connection: AiConnection, model: String, input: RecapInput, language: String?, prompt: String = ""): kotlin.Result<RecapText> {
        val system = buildSystemPrompt(language, prompt)
        val user = "Yesterday (${input.weekday} ${input.date}), JSON:\n" + inputJson(input)
        val startedAt = System.currentTimeMillis()
        var httpError: String? = null
        val response = try {
            api.completeJson(connection.baseUrl, connection.apiKey, model, system, user, "daily_recap", SCHEMA)
        } catch (e: AiHttpException) {
            httpError = e.message
            Result.Error(NetworkError.UNKNOWN)
        }
        val content = (response as? Result.Success)?.data?.choices?.firstOrNull()?.message?.content
        val usage = (response as? Result.Success)?.data?.usage
        val error = when {
            httpError != null -> "request failed: $httpError"
            response is Result.Error -> "request failed: ${response.error}"
            content == null -> "returned an empty response"
            else -> null
        }
        runCatching {
            llmLog.insert(
                LlmRequestLogEntity(
                    timestampUtc = startedAt, purpose = "Daily recap", connectionName = connection.name, model = model,
                    durationMs = System.currentTimeMillis() - startedAt, inputText = system + "\n\n" + user, imageCount = 0,
                    outputText = content, inputTokens = usage?.promptTokens, outputTokens = usage?.completionTokens,
                    success = error == null, error = error
                )
            )
            llmLog.prune(300)
        }.onFailure { Log.e(TAG, "Couldn't log the request", it) }
        if (error != null) return kotlin.Result.failure(IllegalStateException("AI $error"))
        return runCatching { Companion.parse(content!!) }
    }

    companion object {
        internal fun parse(content: String): RecapText {
            // Models sometimes wrap the JSON in fences or prose: take the outermost object.
            val start = content.indexOf('{')
            val end = content.lastIndexOf('}')
            require(start in 0 until end) { "AI response contained no JSON object." }
            val obj = Json.parseToJsonElement(content.substring(start, end + 1)).jsonObject
            fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            fun points(key: String) = (obj[key] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val text = o["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (text.isEmpty()) null else text to o["time"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            }
            val headline = str("headline")
            require(headline.isNotEmpty()) { "AI response had no headline." }
            return RecapText(
                headline = headline,
                summary = str("summary"),
                mood = str("mood").lowercase().takeIf { it in setOf("good", "mixed", "hard") } ?: "mixed",
                wentWell = points("went_well").take(2),
                wentWrong = points("went_wrong").take(2),
                doToday = (obj["do_today"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }.take(3)
            )
        }

        private const val TAG = "DailyRecap"

        /** The user's [custom] prompt, or the built-in one, plus the output language. The reply's JSON schema stays fixed. */
        fun buildSystemPrompt(language: String?, custom: String = ""): String {
            val lang = if (language == null) "" else "\n\nWrite all text in $language (the JSON keys and the mood value stay as specified)."
            return custom.ifBlank { DEFAULT_PROMPT.trim() } + lang
        }

        /** What the settings page shows and edits. */
        const val DEFAULT_PROMPT = """
You write a very short morning recap for a person with type 1 diabetes, based on a summary of their previous day (CGM glucose, meals, insulin, activity and sleep). They read it on their phone's lock screen, so brevity matters more than completeness.

HOW TO READ THE DATA
- Glucose is in mg/dL. "inRangePct" is time in the target range; "belowPct"/"abovePct" are mildly out of range, the "very" ones are severe (below 54 / above 250). A coefficient of variation (cvPct) up to 36 is stable.
- "vsPrevious7Days" is the person's own recent average: judge the day against it as well as against the target. A day can be good "for them" even if it was not perfect.
- "blocks" cut the day into four six-hour stretches; "episodes" are stretches below or above target of at least 15 minutes, with what happened just before ("before"). "meals" carry the glucose at the start, the peak, when it peaked, and the share of the following 4 hours in range.
- Some fields can be missing (no sensor data, no activity tracker). Never mention missing data unless it is the main story, and never invent numbers.

WRITE (about yesterday, the whole day)
- headline: the notification title, as short as possible (at most 28 characters), a plain verdict on the day, no emoji.
- summary: one single sentence (at most 110 characters) about the day, the most important thing about it, for the notification text; it must stand on its own and not repeat the headline word for word.
- mood: "good" (mostly in range, no severe lows), "mixed", or "hard" (severe or repeated lows, or long highs).
- went_well: 0 to 2 items, the genuinely good things (a stable night, a meal handled well, recovery after a high, more steps than usual). Skip it rather than flatter.
- went_wrong: 0 to 2 items (none at all on a good day: a single mild, short excursion of under about 30 minutes is not worth listing). Lows come before highs. Say what happened and, where the data shows it, the likely context (the meal, the workout, the hour); say "probably" when it is an inference.
- do_today: exactly 2 or 3 concrete, small, doable actions for today that follow from the above, or from a device due soon. Behaviour only (when to eat, pre-bolus timing the person already uses, a walk after dinner, carrying fast carbs on a workout day, checking a sensor), phrased as suggestions.
- Every item at most 95 characters, plain words, no jargon, no emoji, no markdown, no exclamation marks. Warm and matter-of-fact; never scolding.
- For went_well and went_wrong items, "time" is the local time "HH:mm" of the moment it is about, taken from the data (an episode start or extreme, a meal time, a peak), so it can be marked on a chart. Use "" when it is about the whole day.

HARD RULES
- Never tell the person to change insulin doses, ratios, basal rates, settings or targets, and never give a dose. Do not interpret symptoms or give medical diagnoses.
- If there were severe lows or several lows, one do_today item may suggest talking about them with their diabetes care team.
- Do not repeat the same point in two sections.

Respond only with JSON matching the provided schema.
"""

        private fun point() = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("text", buildJsonObject { put("type", "string") })
                put("time", buildJsonObject { put("type", "string") })
            })
            put("required", JsonArray(listOf(JsonPrimitive("text"), JsonPrimitive("time"))))
            put("additionalProperties", false)
        }

        private fun list(items: JsonObject) = buildJsonObject { put("type", "array"); put("items", items) }

        val SCHEMA: JsonObject = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("headline", buildJsonObject { put("type", "string") })
                put("summary", buildJsonObject { put("type", "string") })
                put("mood", buildJsonObject {
                    put("type", "string")
                    put("enum", JsonArray(listOf("good", "mixed", "hard").map(::JsonPrimitive)))
                })
                put("went_well", list(point()))
                put("went_wrong", list(point()))
                put("do_today", list(buildJsonObject { put("type", "string") }))
            })
            put("required", JsonArray(listOf("headline", "summary", "mood", "went_well", "went_wrong", "do_today").map(::JsonPrimitive)))
            put("additionalProperties", false)
        }
    }
}
