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
import uk.scimone.diafit.core.data.networking.OpenAiApi
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealAnalysisResult
import uk.scimone.diafit.core.domain.model.ComponentConfidence
import uk.scimone.diafit.core.domain.model.MealComponent
import uk.scimone.diafit.core.domain.repository.MealAnalysisRepository
import uk.scimone.diafit.core.domain.util.networking.Result as NetResult
import uk.scimone.diafit.settings.domain.model.DEFAULT_AI_MODEL
import uk.scimone.diafit.settings.domain.usecase.GetAiConfigUseCase

private const val TAG = "MealAnalysisRepository"
private const val MAX_UPLOAD_EDGE_PX = 1280
private const val UPLOAD_JPEG_QUALITY = 85

private const val MEAL_ANALYSIS_PROMPT = """
You are a nutrition analyst helping a person with type 1 diabetes estimate the nutrients of a meal from photos, mainly to count carbohydrates accurately.

INPUT
One or more photos of food eaten together as one course. Several photos may show different dishes or the same dish from different angles: count every food exactly once. There may also be notes from the person; they know what they actually ate, so follow them over what the photos suggest (e.g. if they ate only half, estimate only that part and say so in the reasoning).

TASK
Split the meal into its distinct components: every separate food, drink, sauce, topping or garnish that adds a meaningful amount of calories or carbs (e.g. soup, bread slice, walnut topping). Merge trivial items (a pinch of herbs) into the component they belong to; don't list water or zero-calorie items. Use 1-12 components, the minimum that describes the meal.

For each component give:
- name: short, specific, in English (e.g. "Whole wheat bread", not "Bread").
- emoji: exactly one emoji that best represents it.
- basis: one short phrase with the portion you assumed and how you judged it (e.g. "1 bowl, ~300 g, creamy").
- weight_g: edible weight in grams (ml for drinks, treat 1 ml = 1 g). Estimate from visible size, plate/cutlery/hand as scale, and typical portions; for cooked food give the cooked weight.
- calories (kcal), carbs_g, sugar_g, fiber_g, protein_g, fat_g for exactly that portion. Be internally consistent: sugar_g and fiber_g are part of carbs_g, and calories should roughly equal 4*carbs + 4*protein + 9*fat. carbs_g is total carbohydrates (fiber included), as on a nutrition label.
- confidence: HIGH (clearly visible, standard food), MEDIUM (portion or recipe uncertain), LOW (hidden or ambiguous content, e.g. sauce, filling, oil).

Then give:
- meal_name: a very short title for the whole meal: 2-5 words, max 35 characters, no punctuation or descriptions (e.g. "Pumpkin soup with bread", "Salmon sushi", "Pasta Bolognese").
- absorption: how long the meal will raise blood sugar: SHORT (mostly fast sugars, drinks, dextrose, up to about 2 h), MEDIUM (ordinary mixed meal, 2-4 h), LONG (high fat/protein or very slow, e.g. pizza, cream sauces, 4 h or more).
- reasoning: very brief, max 2 short sentences (about 200 characters in total). Say how the portions were judged and the key assumption or the least certain component, plus any notes from the person that were applied. Then end with the fixed words "Estimate only, verify before dosing."

Prefer realistic, not conservative, numbers. If a photo shows no food, return an empty components list and explain in the reasoning.
Respond only with JSON matching the provided schema.
"""


class MealAnalysisRepositoryImpl(
    private val context: Context,
    private val openAiApi: OpenAiApi,
    private val getAiConfig: GetAiConfigUseCase
) : MealAnalysisRepository {

    override suspend fun analyzeMealPhotos(imageUris: List<Uri>, userNotes: String?): Result<MealAnalysisResult> =
        withContext(Dispatchers.IO) {
            try {
                val config = getAiConfig()
                if (config.apiKey.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("No AI API key configured in Settings.")
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

                when (
                    val response = openAiApi.analyzeMealPhoto(
                        baseUrl = config.baseUrl,
                        apiKey = config.apiKey,
                        model = config.model.ifBlank { DEFAULT_AI_MODEL },
                        prompt = buildPrompt(userNotes),
                        imagesBase64 = images
                    )
                ) {
                    is NetResult.Success -> {
                        val content = response.data.choices.firstOrNull()?.message?.content
                            ?: return@withContext Result.failure(IllegalStateException("Empty AI response."))
                        Result.success(parseAnalysis(content))
                    }
                    is NetResult.Error -> {
                        Log.e(TAG, "AI meal analysis request failed: ${response.error}")
                        Result.failure(IllegalStateException("AI request failed: ${response.error}"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to analyze meal photo", e)
                Result.failure(e)
            }
        }

    /** Appends the user's notes so the model adjusts quantities (e.g. "I only drank half of the bottle"). */
    private fun buildPrompt(userNotes: String?): String {
        val notes = userNotes?.trim().orEmpty()
        if (notes.isEmpty()) return MEAL_ANALYSIS_PROMPT
        return MEAL_ANALYSIS_PROMPT + """

Notes from the person eating (they know what they actually ate; follow them over what the photos suggest, e.g. if they only ate or drank part of something, count only that part and say so in the reasoning):
$notes
"""
    }

    override suspend fun listModels(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val config = getAiConfig()
            if (config.apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Enter an API key first."))
            }
            when (val response = openAiApi.listModels(config.baseUrl, config.apiKey)) {
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
