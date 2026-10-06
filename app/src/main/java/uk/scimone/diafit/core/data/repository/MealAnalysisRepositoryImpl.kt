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
import uk.scimone.diafit.core.domain.model.MealDish
import uk.scimone.diafit.core.domain.model.MealIngredient
import uk.scimone.diafit.core.domain.repository.MealAnalysisRepository
import uk.scimone.diafit.core.domain.util.networking.Result as NetResult
import uk.scimone.diafit.settings.domain.model.DEFAULT_AI_MODEL
import uk.scimone.diafit.settings.domain.usecase.GetAiConfigUseCase

private const val TAG = "MealAnalysisRepository"
private const val MAX_UPLOAD_EDGE_PX = 1280
private const val UPLOAD_JPEG_QUALITY = 85

private const val MEAL_ANALYSIS_PROMPT = """
You are a highly accurate and detailed food recognition and nutrition analysis expert, helping a person with type 1 diabetes count carbohydrates.

You get one or more photos of food that is eaten together, as one course. Photos may show different dishes (e.g. a main dish and a dessert, or several small plates) or the same dish from different angles: count every food exactly once and never double count a dish that appears in more than one photo. Give totals for everything shown.

Provide a structured JSON output containing:

1. Dish Name: a short name for everything shown together (e.g. "Chicken Caesar Salad", "Salmon nigiri and California maki", "Pasta Bolognese with tiramisu"). Be as specific as possible.

2. Dishes: each distinct dish or plate with its own carbohydrate estimate in grams (one entry if there is only one dish).

3. Ingredients: all identifiable ingredients, including sauces/seasonings/garnishes, as granular as possible, each with an estimated quantity/weight.

4. Macronutrient Breakdown (total for everything shown): Calories (kcal), Protein (g), Carbohydrates (g), Fat (g), Fiber (g), Sugar (g), Sodium (mg). Carbohydrates must equal the sum of the dishes.

5. Reasoning: a precise and analytical breakdown of the carb calculation.

6. Meal impact duration: "SHORT" (e.g. dextrose/juice), "MEDIUM", or "LONG" (e.g. a cheesy pizza): an estimate of how long the food will affect blood sugar.

Output strictly as JSON, e.g.:
{
  "dish_name": "",
  "dishes": [{"name": "", "carbohydrates": 0}],
  "ingredients": [{"name": "", "quantity": ""}],
  "macronutrients": {
    "calories": 0, "protein": 0, "carbohydrates": 0, "fat": 0, "fiber": 0, "sugar": 0, "sodium": 0
  },
  "reasoning": "",
  "meal_impact_duration": "SHORT | MEDIUM | LONG"
}
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

    private fun intOrNull(obj: JsonObject?, key: String): Int? {
        val element = obj?.get(key) ?: return null
        if (element is JsonNull) return null
        return (element as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
    }

    private fun parseAnalysis(content: String): MealAnalysisResult {
        // Models may wrap the JSON in code fences or add prose; take the outermost {...}.
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        require(start in 0 until end) { "AI response contained no JSON object." }
        val json = Json.parseToJsonElement(content.substring(start, end + 1)).jsonObject

        val ingredients = (json["ingredients"] as? JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            MealIngredient(
                name = stringOrNull(obj, "name") ?: "",
                quantity = stringOrNull(obj, "quantity")
            )
        } ?: emptyList()

        val dishes = (json["dishes"] as? JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = stringOrNull(obj, "name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MealDish(name = name, carbohydrates = intOrNull(obj, "carbohydrates"))
        } ?: emptyList()

        val macros = json["macronutrients"] as? JsonObject

        val impactType = when (stringOrNull(json, "meal_impact_duration")?.uppercase()) {
            "SHORT" -> ImpactType.SHORT
            "LONG" -> ImpactType.LONG
            else -> ImpactType.MEDIUM
        }

        return MealAnalysisResult(
            dishName = stringOrNull(json, "dish_name"),
            dishes = dishes,
            ingredients = ingredients,
            calories = intOrNull(macros, "calories"),
            protein = intOrNull(macros, "protein"),
            carbohydrates = intOrNull(macros, "carbohydrates"),
            fat = intOrNull(macros, "fat"),
            reasoning = stringOrNull(json, "reasoning"),
            impactType = impactType
        )
    }
}
