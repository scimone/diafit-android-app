package uk.scimone.diafit.core.data.repository

import android.content.Context
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
import uk.scimone.diafit.core.data.networking.OpenAiApi
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealAnalysisResult
import uk.scimone.diafit.core.domain.model.MealIngredient
import uk.scimone.diafit.core.domain.repository.MealAnalysisRepository
import uk.scimone.diafit.core.domain.util.networking.Result as NetResult
import uk.scimone.diafit.settings.domain.usecase.GetAiConfigUseCase

private const val TAG = "MealAnalysisRepository"

private const val MEAL_ANALYSIS_PROMPT = """
You are a highly accurate and detailed food recognition and nutrition analysis expert.

Given the following image of a meal, provide a structured JSON output containing the following information:

1. Dish Name: (Most likely name of the entire meal, e.g., "Chicken Caesar Salad", "Pasta Bolognese", or "Mixed Vegetable Curry"). Be as specific as possible.

2. Ingredients: (A list of all identifiable ingredients in the image, including sauces/seasonings/garnishes, as granular as possible, each with an estimated quantity/weight).

3. Macronutrient Breakdown (per serving): Calories (kcal), Protein (g), Carbohydrates (g), Fat (g), Fiber (g), Sugar (g), Sodium (mg).

4. Reasoning: a precise and analytical breakdown of the carb calculation.

5. Meal impact duration: "SHORT" (e.g. dextrose/juice), "MEDIUM", or "LONG" (e.g. a cheesy pizza) — an estimate of how long the meal will affect blood sugar.

Output strictly as JSON, e.g.:
{
  "dish_name": "",
  "ingredients": [{"name": "", "quantity": ""}],
  "macronutrients": {
    "calories": 0, "protein": 0, "carbohydrates": 0, "fat": 0, "fiber": 0, "sugar": 0, "sodium": 0
  },
  "reasoning": "",
  "meal_impact_duration": "SHORT | MEDIUM | LONG"
}
"""

private const val DEFAULT_MODEL = "gpt-4o-mini"

class MealAnalysisRepositoryImpl(
    private val context: Context,
    private val openAiApi: OpenAiApi,
    private val getAiConfig: GetAiConfigUseCase
) : MealAnalysisRepository {

    override suspend fun analyzeMealPhoto(imageUri: Uri): Result<MealAnalysisResult> =
        withContext(Dispatchers.IO) {
            try {
                val config = getAiConfig()
                if (config.apiKey.isBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("No AI API key configured in Settings.")
                    )
                }

                val imageBytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                    ?: return@withContext Result.failure(IllegalStateException("Could not read meal photo."))
                val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

                when (
                    val response = openAiApi.analyzeMealPhoto(
                        baseUrl = config.baseUrl,
                        apiKey = config.apiKey,
                        model = DEFAULT_MODEL,
                        prompt = MEAL_ANALYSIS_PROMPT,
                        imageBase64 = imageBase64,
                        imageMimeType = "image/jpeg"
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
        val json = Json.parseToJsonElement(content).jsonObject

        val ingredients = (json["ingredients"] as? JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            MealIngredient(
                name = stringOrNull(obj, "name") ?: "",
                quantity = stringOrNull(obj, "quantity")
            )
        } ?: emptyList()

        val macros = json["macronutrients"] as? JsonObject

        val impactType = when (stringOrNull(json, "meal_impact_duration")?.uppercase()) {
            "SHORT" -> ImpactType.SHORT
            "LONG" -> ImpactType.LONG
            else -> ImpactType.MEDIUM
        }

        return MealAnalysisResult(
            dishName = stringOrNull(json, "dish_name"),
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
