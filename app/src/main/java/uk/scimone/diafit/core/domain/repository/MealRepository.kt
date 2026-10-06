package uk.scimone.diafit.core.domain.repository

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.MealEntity

interface MealRepository {

    suspend fun createMeal(meal: MealEntity): Result<Unit>

    suspend fun getMealById(id: Int): MealEntity?

    suspend fun updateMeal(meal: MealEntity): Result<Unit>

    /** Soft delete (`isValid = false`) or restore. */
    suspend fun setMealValid(id: Int, isValid: Boolean): Result<Unit>

    suspend fun updateMealImage(id: Int, imageId: String): Result<Unit>

    suspend fun existsBySourceId(sourceId: String): Boolean
    suspend fun existsImportedAt(mealTimeUtc: Long, carbohydrates: Int): Boolean

    suspend fun getMealsByUserId(userId: Int): Result<List<MealEntity>>

    fun observeMealsByUserId(userId: Int): Flow<List<MealEntity>>

    fun getAllMealsSince(startTime: Long, userId: Int): Flow<List<MealEntity>>

}
