package uk.scimone.diafit.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.RecipeEntity

@Dao
interface RecipeDao {
    /** Most recently used (or, never used, created) first. */
    @Query("SELECT * FROM RecipeEntity ORDER BY COALESCE(lastUsedUtc, updatedUtc) DESC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM RecipeEntity WHERE id = :id")
    suspend fun get(id: Int): RecipeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recipe: RecipeEntity): Long

    @Query("UPDATE RecipeEntity SET lastUsedUtc = :now, useCount = useCount + 1 WHERE id = :id")
    suspend fun markUsed(id: Int, now: Long)

    @Query("DELETE FROM RecipeEntity WHERE id = :id")
    suspend fun delete(id: Int)
}
