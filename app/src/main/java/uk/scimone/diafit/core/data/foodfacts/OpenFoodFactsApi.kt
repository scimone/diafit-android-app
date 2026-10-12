package uk.scimone.diafit.core.data.foodfacts

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import uk.scimone.diafit.core.domain.model.FoodProduct
import uk.scimone.diafit.core.domain.model.NutrientsPer100
import uk.scimone.diafit.core.domain.model.ProductLookup

private const val TAG = "OpenFoodFacts"
private const val BASE_URL = "https://world.openfoodfacts.org/api/v2/product"
private const val FIELDS = "code,product_name,product_name_en,generic_name,brands,nutriments,serving_size,serving_quantity,product_quantity"

/** Looks a barcode up in the Open Food Facts database (https://openfoodfacts.github.io/openfoodfacts-server/api/). */
class OpenFoodFactsApi(private val client: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Text search; products without carbohydrate data are left out. */
    suspend fun search(query: String): Result<List<FoodProduct>> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("https://world.openfoodfacts.org/cgi/search.pl") {
                parameter("search_terms", query)
                parameter("search_simple", 1)
                parameter("action", "process")
                parameter("json", 1)
                parameter("page_size", 12)
                parameter("fields", FIELDS)
                header("User-Agent", "Diafit/1.0 (Android; scimone.mail@gmail.com)")
                timeout { requestTimeoutMillis = 15_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 15_000 }
            }
            if (response.status != HttpStatusCode.OK) return@withContext Result.failure(IllegalStateException("Server answered ${response.status.value}"))
            val products = json.parseToJsonElement(response.bodyAsText()).jsonObject["products"] as? kotlinx.serialization.json.JsonArray
            Result.success(products.orEmpty().mapNotNull { el ->
                val p = el.jsonObject
                p.text("code")?.let { parseProduct(it, p) }
            })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Search for $query failed", e)
            Result.failure(e)
        }
    }

    suspend fun lookup(barcode: String): ProductLookup = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$BASE_URL/$barcode.json") {
                parameter("fields", FIELDS)
                // Open Food Facts asks every app to identify itself.
                header("User-Agent", "Diafit/1.0 (Android; scimone.mail@gmail.com)")
                timeout { requestTimeoutMillis = 12_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 12_000 }
            }
            if (response.status == HttpStatusCode.NotFound) return@withContext ProductLookup.NotFound(barcode)
            if (response.status != HttpStatusCode.OK) return@withContext ProductLookup.Failed(barcode, "Server answered ${response.status.value}")
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val product = root["product"] as? JsonObject
            if (root["status"]?.num() == 0.0 || product == null) return@withContext ProductLookup.NotFound(barcode)
            parseProduct(barcode, product)?.let { ProductLookup.Found(it) } ?: ProductLookup.NotFound(barcode)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Lookup of $barcode failed", e)
            ProductLookup.Failed(barcode, "No connection to the food database")
        }
    }
}

private fun kotlinx.serialization.json.JsonElement.num(): Double? =
    (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.replace(',', '.')?.toDoubleOrNull() }

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

/** Maps the API's product to ours; null when it has no carbohydrate data (nothing useful for dosing). */
internal fun parseProduct(barcode: String, p: JsonObject): FoodProduct? {
    val n = p["nutriments"] as? JsonObject ?: return null
    fun per100(key: String) = n["${key}_100g"]?.num()
    val carbs = per100("carbohydrates") ?: return null
    val protein = per100("proteins") ?: 0.0
    val fat = per100("fat") ?: 0.0
    // Energy is often missing or only in kJ: derive it from the macros then.
    val kcal = per100("energy-kcal") ?: per100("energy")?.let { it / 4.184 } ?: (4 * carbs + 4 * protein + 9 * fat)
    val name = p.text("product_name") ?: p.text("product_name_en") ?: p.text("generic_name") ?: "Product $barcode"
    val brand = p.text("brands")?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() && !name.contains(it, ignoreCase = true) }
    val serving = p["serving_quantity"]?.num()?.takeIf { it > 0 }
    return FoodProduct(
        barcode = barcode,
        name = name,
        brand = brand,
        per100 = NutrientsPer100(
            kcal = kcal,
            carbsG = carbs,
            sugarG = per100("sugars") ?: 0.0,
            fiberG = per100("fiber") ?: 0.0,
            proteinG = protein,
            fatG = fat
        ),
        servingG = serving,
        packageG = p["product_quantity"]?.num()?.takeIf { it > 0 }
    )
}
