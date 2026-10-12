package uk.scimone.diafit.core.data.foodfacts

import uk.scimone.diafit.core.domain.model.ProductLookup
import uk.scimone.diafit.core.domain.repository.FoodProductRepository

/** Open Food Facts first; when it has nothing (or can't be reached), USDA FoodData Central (if the user set a key). */
class FoodProductRepositoryImpl(
    private val openFoodFacts: OpenFoodFactsApi,
    private val usda: UsdaFoodDataApi
) : FoodProductRepository {

    override suspend fun lookup(barcode: String): ProductLookup {
        val first = openFoodFacts.lookup(barcode)
        if (first is ProductLookup.Found) return first
        return usda.lookup(barcode).takeIf { it is ProductLookup.Found } ?: first
    }

    override suspend fun testUsdaKey(apiKey: String) = usda.testKey(apiKey)
}
