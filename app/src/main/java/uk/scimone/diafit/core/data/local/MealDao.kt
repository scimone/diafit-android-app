package uk.scimone.diafit.core.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.MealEntity
import java.util.UUID

@Dao
interface MealDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealEntity)

    @Update
    suspend fun updateMeal(meal: MealEntity)

    @Query("UPDATE MealEntity SET imageId = :imageId WHERE id = :id")
    suspend fun updateImageId(id: Int, imageId: String)

    @Delete
    suspend fun deleteMeal(meal: MealEntity)

    @Query("SELECT * FROM MealEntity WHERE id = :mealId LIMIT 1")
    suspend fun getMealById(mealId: UUID): MealEntity?

    @Query("SELECT * FROM MealEntity ORDER BY mealTimeUtc DESC")
    fun getAllMeals(): Flow<List<MealEntity>>

    @Query("SELECT * FROM MealEntity WHERE userId = :userId ORDER BY mealTimeUtc DESC")
    suspend fun getMealsByUserId(userId: Int): List<MealEntity>

    @Query("SELECT * FROM MealEntity WHERE userId = :userId ORDER BY mealTimeUtc DESC")
    fun observeMealsByUserId(userId: Int): Flow<List<MealEntity>>

    @Query("SELECT COUNT(*) FROM MealEntity WHERE sourceId = :sourceId")
    suspend fun countBySourceId(sourceId: String): Int

    /** Imported (sourceId-tagged) meals with this exact time and carbs; catches the same entry arriving with a different sourceId. */
    @Query("SELECT COUNT(*) FROM MealEntity WHERE sourceId IS NOT NULL AND mealTimeUtc = :mealTimeUtc AND carbohydrates = :carbohydrates")
    suspend fun countImportedAt(mealTimeUtc: Long, carbohydrates: Int): Int

    @Query("SELECT * FROM MealEntity WHERE mealTimeUtc >= :startTime AND userId = :userId ORDER BY mealTimeUtc ASC")
    fun getAllMealsSince(startTime: Long, userId: Int): Flow<List<MealEntity>>
}
