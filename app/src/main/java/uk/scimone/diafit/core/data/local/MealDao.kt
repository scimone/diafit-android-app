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

    @Query("SELECT * FROM MealEntity WHERE mealTimeUtc >= :startTime AND userId = :userId ORDER BY mealTimeUtc ASC")
    fun getAllMealsSince(startTime: Long, userId: Int): Flow<List<MealEntity>>
}
