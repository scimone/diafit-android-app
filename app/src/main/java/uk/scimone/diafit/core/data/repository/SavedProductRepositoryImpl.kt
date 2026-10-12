package uk.scimone.diafit.core.data.repository

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.data.local.SavedProductDao
import uk.scimone.diafit.core.domain.model.FoodProduct
import uk.scimone.diafit.core.domain.model.SavedProductEntity
import uk.scimone.diafit.core.domain.repository.SavedProductRepository

class SavedProductRepositoryImpl(private val dao: SavedProductDao) : SavedProductRepository {
    override fun observeAll(): Flow<List<SavedProductEntity>> = dao.observeAll()

    override suspend fun get(barcode: String): FoodProduct? = dao.get(barcode)?.toProduct()

    override suspend fun remember(product: FoodProduct) {
        val existing = dao.get(product.barcode)
        dao.upsert(SavedProductEntity.of(product, System.currentTimeMillis(), (existing?.useCount ?: 0) + 1).copy(isFavorite = existing?.isFavorite ?: false))
    }

    override suspend fun isFavorite(barcode: String) = dao.get(barcode)?.isFavorite ?: false

    override suspend fun setFavorite(barcode: String, favorite: Boolean) = dao.setFavorite(barcode, favorite)

    override suspend fun forget(barcode: String) = dao.delete(barcode)
}
