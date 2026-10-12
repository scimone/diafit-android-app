package uk.scimone.diafit.core.domain.repository

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.FoodProduct
import uk.scimone.diafit.core.domain.model.SavedProductEntity

interface SavedProductRepository {
    /** Most recently used first. */
    fun observeAll(): Flow<List<SavedProductEntity>>
    suspend fun get(barcode: String): FoodProduct?
    /** Remembers [product] (or refreshes it) and counts one more use. */
    suspend fun remember(product: FoodProduct)
    suspend fun isFavorite(barcode: String): Boolean
    suspend fun setFavorite(barcode: String, favorite: Boolean)
    suspend fun forget(barcode: String)
}
