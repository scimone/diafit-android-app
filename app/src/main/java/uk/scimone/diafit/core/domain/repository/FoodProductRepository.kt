package uk.scimone.diafit.core.domain.repository

import uk.scimone.diafit.core.domain.model.ProductLookup

interface FoodProductRepository {
    suspend fun lookup(barcode: String): ProductLookup

    /** Checks a USDA FoodData Central API key with a small search; failure carries a message for the user. */
    suspend fun testUsdaKey(apiKey: String): Result<Unit>
}
