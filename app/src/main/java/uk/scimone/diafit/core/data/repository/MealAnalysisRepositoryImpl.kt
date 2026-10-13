package uk.scimone.diafit.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import uk.scimone.diafit.core.data.networking.AiHttpException
import uk.scimone.diafit.core.data.networking.OpenAiApi
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealAnalysisResult
import uk.scimone.diafit.core.domain.model.ComponentConfidence
import uk.scimone.diafit.core.domain.model.MealComponent
import uk.scimone.diafit.core.domain.repository.MealAnalysisRepository
import uk.scimone.diafit.core.domain.util.networking.Result as NetResult
import uk.scimone.diafit.settings.domain.model.DEFAULT_AI_MODEL
import uk.scimone.diafit.core.data.local.LlmLogDao
import uk.scimone.diafit.core.domain.model.LlmRequestLogEntity
import uk.scimone.diafit.settings.domain.model.AiConnection
import uk.scimone.diafit.settings.domain.repository.SettingsRepository

private const val TAG = "MealAnalysisRepository"
private const val MAX_UPLOAD_EDGE_PX = 1280
private const val UPLOAD_JPEG_QUALITY = 85
private const val LOG_KEEP = 300



class MealAnalysisRepositoryImpl(
    private val context: Context,
    private val openAiApi: OpenAiApi,
    private val settings: SettingsRepository,
    private val llmLog: LlmLogDao
) : MealAnalysisRepository {

    override suspend fun analyzeMealPhotos(imageUris: List<Uri>, userNotes: String?): Result<MealAnalysisResult> =
        withContext(Dispatchers.IO) {
            try {
                if (!settings.isAiEnabled()) {
                    return@withContext Result.failure(IllegalStateException("AI is switched off in the menu."))
                }
                val mealSettings = settings.getMealEstimationSettings()
                val connections = settings.getAiConnections()
                val connection = connections.firstOrNull { it.id == mealSettings.connectionId } ?: connections.firstOrNull()
                if (connection == null || connection.apiKey.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("No AI connection set up. Add one under Menu → AI.")
                    )
                }

                if (imageUris.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("No photos to analyse."))
                }
                val images = imageUris.map { uri ->
                    val bytes = encodeForUpload(uri)
                        ?: return@withContext Result.failure(IllegalStateException("Could not read a meal photo."))
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }

                val model = mealSettings.model.ifBlank { DEFAULT_AI_MODEL }
                val prompt = buildPrompt(mealSettings.effectivePrompt, userNotes)
                val startedAt = System.currentTimeMillis()
                var httpError: String? = null
                val response = try {
                    openAiApi.analyzeMealPhoto(
                        baseUrl = connection.baseUrl,
                        apiKey = connection.apiKey,
                        model = model,
                        prompt = prompt,
                        imagesBase64 = images
                    )
                } catch (e: AiHttpException) {
                    httpError = e.message
                    NetResult.Error(uk.scimone.diafit.core.domain.util.networking.NetworkError.UNKNOWN)
                }
                val duration = System.currentTimeMillis() - startedAt
                val content = (response as? NetResult.Success)?.data?.choices?.firstOrNull()?.message?.content
                val usage = (response as? NetResult.Success)?.data?.usage
                val error = when {
                    httpError != null -> "request failed: $httpError"
                    response is NetResult.Error -> "request failed: ${response.error}"
                    content == null -> "returned an empty response"
                    else -> null
                }
                logRequest(
                    LlmRequestLogEntity(
                        timestampUtc = startedAt, purpose = "Meal estimate", connectionName = connection.name, model = model,
                        durationMs = duration, inputText = prompt, imageCount = images.size, outputText = content,
                        inputTokens = usage?.promptTokens, outputTokens = usage?.completionTokens,
                        success = error == null, error = error
                    )
                )
                if (error != null) {
                    Log.e(TAG, "AI meal analysis failed: $error")
                    return@withContext Result.failure(IllegalStateException("AI $error"))
                }
                Result.success(parseAnalysis(content!!))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to analyze meal photo", e)
                Result.failure(e)
            }
        }

    private suspend fun logRequest(entry: LlmRequestLogEntity) {
        runCatching {
            llmLog.insert(entry)
            llmLog.prune(LOG_KEEP)
        }.onFailure { Log.e(TAG, "Couldn't log LLM request", it) }
    }

    /** Appends the user's notes so the model adjusts quantities (e.g. "I only drank half of the bottle"). */
    private fun buildPrompt(base: String, userNotes: String?): String {
        val notes = userNotes?.trim().orEmpty()
        // The prompt stays English; only the wording of the free-text answer follows the app language.
        val language = when (java.util.Locale.getDefault().language) { "de" -> "German"; "it" -> "Italian"; else -> null }
        val withLanguage = if (language == null) base else base + "\n\nWrite the reasoning and every dish and food name in $language (the JSON keys and enum values stay as specified)."
        if (notes.isEmpty()) return withLanguage
        return withLanguage + """

Notes from the person eating (they know what they actually ate; follow them over what the photos suggest, e.g. if they only ate or drank part of something, count only that part and say so in the reasoning):
$notes
"""
    }

    override suspend fun listModels(connection: AiConnection): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            if (connection.apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Enter an API key first."))
            }
            when (val response = openAiApi.listModels(connection.baseUrl, connection.apiKey)) {
                is NetResult.Success -> Result.success(response.data.data.map { it.id }.sorted())
                is NetResult.Error -> {
                    Log.e(TAG, "Listing AI models failed: ${response.error}")
                    Result.failure(IllegalStateException("Could not load models: ${response.error}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list AI models", e)
            Result.failure(e)
        }
    }

    /**
     * Camera photos are several MB each; a few of them would make the request huge and slow, and the
     * model doesn't need the detail. Decodes (honouring EXIF rotation, any format incl. HEIC) scaled
     * so the long edge is at most [MAX_UPLOAD_EDGE_PX], and re-encodes as JPEG.
     */
    private fun encodeForUpload(uri: Uri): ByteArray? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longEdge = maxOf(info.size.width, info.size.height)
            if (longEdge > MAX_UPLOAD_EDGE_PX) {
                val scale = MAX_UPLOAD_EDGE_PX.toFloat() / longEdge
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, UPLOAD_JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }.onFailure { Log.e(TAG, "Couldn't decode $uri", it) }.getOrNull()

    private fun stringOrNull(obj: JsonObject, key: String): String? {
        val element = obj[key] ?: return null
        if (element is JsonNull) return null
        return (element as? JsonPrimitive)?.content
    }

    private fun doubleOrZero(obj: JsonObject, key: String): Double {
        val element = obj[key] ?: return 0.0
        if (element is JsonNull) return 0.0
        return (element as? JsonPrimitive)?.content?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    }

    private fun parseAnalysis(content: String): MealAnalysisResult {
        // Models may wrap the JSON in code fences or add prose; take the outermost {...}.
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        require(start in 0 until end) { "AI response contained no JSON object." }
        val json = Json.parseToJsonElement(content.substring(start, end + 1)).jsonObject

        val components = (json["components"] as? JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = stringOrNull(obj, "name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MealComponent(
                name = name,
                emoji = stringOrNull(obj, "emoji")?.trim()?.takeIf { it.isNotEmpty() } ?: "🍽️",
                basis = stringOrNull(obj, "basis")?.takeIf { it.isNotBlank() },
                weightG = doubleOrZero(obj, "weight_g"),
                calories = doubleOrZero(obj, "calories"),
                carbsG = doubleOrZero(obj, "carbs_g"),
                sugarG = doubleOrZero(obj, "sugar_g"),
                fiberG = doubleOrZero(obj, "fiber_g"),
                proteinG = doubleOrZero(obj, "protein_g"),
                fatG = doubleOrZero(obj, "fat_g"),
                confidence = when (stringOrNull(obj, "confidence")?.uppercase()) {
                    "LOW" -> ComponentConfidence.LOW
                    "HIGH" -> ComponentConfidence.HIGH
                    else -> ComponentConfidence.MEDIUM
                }
            )
        } ?: emptyList()

        val impactType = when (stringOrNull(json, "absorption")?.uppercase()) {
            "SHORT" -> ImpactType.SHORT
            "LONG" -> ImpactType.LONG
            else -> ImpactType.MEDIUM
        }

        return MealAnalysisResult(
            mealName = stringOrNull(json, "meal_name"),
            components = components,
            reasoning = stringOrNull(json, "reasoning"),
            impactType = impactType
        )
    }
}
