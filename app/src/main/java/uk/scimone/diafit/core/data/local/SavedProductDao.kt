package uk.scimone.diafit.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.SavedProductEntity

@Dao
interface SavedProductDao {
    @Query("SELECT * FROM SavedProductEntity ORDER BY lastUsedUtc DESC")
    fun observeAll(): Flow<List<SavedProductEntity>>

    @Query("SELECT * FROM SavedProductEntity WHERE barcode = :barcode")
    suspend fun get(barcode: String): SavedProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: SavedProductEntity)

    @Query("UPDATE SavedProductEntity SET isFavorite = :favorite WHERE barcode = :barcode")
    suspend fun setFavorite(barcode: String, favorite: Boolean)

    @Query("DELETE FROM SavedProductEntity WHERE barcode = :barcode")
    suspend fun delete(barcode: String)
}
