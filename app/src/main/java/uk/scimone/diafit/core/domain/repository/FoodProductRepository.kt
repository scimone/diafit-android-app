package uk.scimone.diafit.core.domain.repository

import uk.scimone.diafit.core.domain.model.ProductLookup

interface FoodProductRepository {
    suspend fun lookup(barcode: String): ProductLookup

    /** Products matching a name, from every database that answers (best matches of each interleaved); fails only when none did. */
    suspend fun searchByName(query: String): Result<List<uk.scimone.diafit.core.domain.model.FoodProduct>>

    /** Checks a USDA FoodData Central API key with a small search; failure carries a message for the user. */
    suspend fun testUsdaKey(apiKey: String): Result<Unit>
}
