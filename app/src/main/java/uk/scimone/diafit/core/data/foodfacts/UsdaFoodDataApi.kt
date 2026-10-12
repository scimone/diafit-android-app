package uk.scimone.diafit.core.data.foodfacts

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import uk.scimone.diafit.core.domain.model.FoodProduct
import uk.scimone.diafit.core.domain.model.NutrientsPer100
import uk.scimone.diafit.core.domain.model.ProductLookup
import uk.scimone.diafit.core.domain.model.SOURCE_USDA
import uk.scimone.diafit.settings.domain.repository.SettingsRepository

private const val TAG = "UsdaFoodData"
private const val SEARCH_URL = "https://api.nal.usda.gov/fdc/v1/foods/search"

/**
 * The USDA FoodData Central branded-food database (https://fdc.nal.usda.gov/api-guide), the fallback
 * for barcodes Open Food Facts doesn't know. Needs the user's free API key; values are per 100 g.
 */
class UsdaFoodDataApi(private val client: HttpClient, private val settings: SettingsRepository) {

    private val json = Json { ignoreUnknownKeys = true }

    /** [ProductLookup.NotFound] also when no key is set: the fallback simply isn't available. */
    suspend fun lookup(barcode: String): ProductLookup = withContext(Dispatchers.IO) {
        val key = settings.getUsdaApiKey()
        if (key.isBlank()) return@withContext ProductLookup.NotFound(barcode)
        try {
            val response = client.get(SEARCH_URL) {
                parameter("query", barcode)
                parameter("dataType", "Branded")
                parameter("pageSize", 5)
                parameter("api_key", key)
                timeout { requestTimeoutMillis = 12_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 12_000 }
            }
            if (response.status != HttpStatusCode.OK) {
                Log.w(TAG, "Search answered ${response.status.value}")
                return@withContext ProductLookup.NotFound(barcode)
            }
            val foods = json.parseToJsonElement(response.bodyAsText()).jsonObject["foods"] as? JsonArray ?: return@withContext ProductLookup.NotFound(barcode)
            foods.map { it.jsonObject }
                .firstNotNullOfOrNull { parseFdcFood(barcode, it) }
                ?.let { ProductLookup.Found(it) } ?: ProductLookup.NotFound(barcode)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Lookup of $barcode failed", e)
            ProductLookup.NotFound(barcode)
        }
    }

    suspend fun testKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("Enter an API key first."))
        try {
            val response = client.get(SEARCH_URL) {
                parameter("query", "apple")
                parameter("pageSize", 1)
                parameter("api_key", apiKey.trim())
                timeout { requestTimeoutMillis = 12_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 12_000 }
            }
            when (response.status.value) {
                200 -> Result.success(Unit)
                400, 401, 403 -> Result.failure(IllegalStateException("The key was rejected. Check it and try again."))
                429 -> Result.failure(IllegalStateException("Rate limit reached for this key. Try again later."))
                else -> Result.failure(IllegalStateException("Server answered ${response.status.value}."))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Key test failed", e)
            Result.failure(IllegalStateException("No connection to the USDA database."))
        }
    }
}

/** Branded foods carry the barcode zero-padded to 14 digits; compare without leading zeros. */
private fun String.gtin() = trimStart('0')

private fun JsonObject.num(key: String): Double? =
    (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

/** The search result when it is exactly this barcode and has carbohydrate data, else null. */
internal fun parseFdcFood(barcode: String, food: JsonObject): FoodProduct? {
    val code = food.text("gtinUpc") ?: return null
    if (code.gtin() != barcode.gtin()) return null
    val nutrients = (food["foodNutrients"] as? JsonArray)?.map { it.jsonObject }.orEmpty()
    fun value(id: Int) = nutrients.firstOrNull { it.num("nutrientId")?.toInt() == id }?.num("value")
    val carbs = value(1005) ?: return null
    val protein = value(1003) ?: 0.0
    val fat = value(1004) ?: 0.0
    val name = food.text("description")?.let { if (it.any(Char::isLowerCase)) it else it.lowercase().replaceFirstChar(Char::uppercase) } ?: return null
    val unit = food.text("servingSizeUnit")?.lowercase()
    val serving = food.num("servingSize")?.takeIf { it > 0 && (unit == "g" || unit == "grm" || unit == "ml" || unit == "mlt") }
    return FoodProduct(
        barcode = barcode,
        name = name,
        brand = (food.text("brandName") ?: food.text("brandOwner"))?.takeIf { !name.contains(it, ignoreCase = true) },
        per100 = NutrientsPer100(
            kcal = value(1008) ?: (4 * carbs + 4 * protein + 9 * fat),
            carbsG = carbs,
            sugarG = value(2000) ?: 0.0,
            fiberG = value(1079) ?: 0.0,
            proteinG = protein,
            fatG = fat
        ),
        servingG = serving,
        packageG = null,
        source = SOURCE_USDA
    )
}
