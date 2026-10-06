package uk.scimone.diafit.core.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.MealEntity

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
    suspend fun getMealById(mealId: Int): MealEntity?

    /** Soft delete / restore: invalid rows stay in the table so synced imports aren't re-created. */
    @Query("UPDATE MealEntity SET isValid = :isValid WHERE id = :mealId")
    suspend fun setValid(mealId: Int, isValid: Boolean)

    @Query("SELECT * FROM MealEntity WHERE isValid = 1 ORDER BY mealTimeUtc DESC")
    fun getAllMeals(): Flow<List<MealEntity>>

    @Query("SELECT * FROM MealEntity WHERE userId = :userId AND isValid = 1 ORDER BY mealTimeUtc DESC")
    suspend fun getMealsByUserId(userId: Int): List<MealEntity>

    @Query("SELECT * FROM MealEntity WHERE userId = :userId AND isValid = 1 ORDER BY mealTimeUtc DESC")
    fun observeMealsByUserId(userId: Int): Flow<List<MealEntity>>

    @Query("SELECT COUNT(*) FROM MealEntity WHERE sourceId = :sourceId")
    suspend fun countBySourceId(sourceId: String): Int

    /** Imported (sourceId-tagged) meals with this exact time and carbs; catches the same entry arriving with a different sourceId. */
    @Query("SELECT COUNT(*) FROM MealEntity WHERE sourceId IS NOT NULL AND mealTimeUtc = :mealTimeUtc AND carbohydrates = :carbohydrates")
    suspend fun countImportedAt(mealTimeUtc: Long, carbohydrates: Int): Int

    @Query("SELECT * FROM MealEntity WHERE sittingId = :sittingId AND isValid = 1 ORDER BY mealTimeUtc ASC")
    suspend fun getMealsBySitting(sittingId: String): List<MealEntity>

    @Query("UPDATE MealEntity SET sittingId = :sittingId WHERE id = :mealId")
    suspend fun setSittingId(mealId: Int, sittingId: String)

    @Query("UPDATE MealEntity SET isValid = :isValid WHERE id IN (:mealIds)")
    suspend fun setValid(mealIds: List<Int>, isValid: Boolean)

    /** Newest logged (not imported) course at or before [now]. */
    @Query("SELECT * FROM MealEntity WHERE userId = :userId AND isValid = 1 AND sourceId IS NULL AND mealTimeUtc <= :now ORDER BY mealTimeUtc DESC LIMIT 1")
    suspend fun getLatestLoggedMeal(userId: Int, now: Long): MealEntity?

    @Query("SELECT * FROM MealEntity WHERE mealTimeUtc >= :startTime AND userId = :userId AND isValid = 1 ORDER BY mealTimeUtc ASC")
    fun getAllMealsSince(startTime: Long, userId: Int): Flow<List<MealEntity>>
}
