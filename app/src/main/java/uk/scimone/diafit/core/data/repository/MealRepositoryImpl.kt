package uk.scimone.diafit.core.data.repository

import android.util.Log
import uk.scimone.diafit.core.data.local.MealDao
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.repository.MealRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val TAG = "MealRepositoryImpl"

class MealRepositoryImpl(
    private val mealDao: MealDao
) : MealRepository {

    override suspend fun createMeal(meal: MealEntity): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            mealDao.insertMeal(meal)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert meal", e)
            Result.failure(e)
        }
    }

    override suspend fun getMealById(id: Int): MealEntity? = withContext(Dispatchers.IO) { mealDao.getMealById(id) }

    override suspend fun updateMeal(meal: MealEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mealDao.updateMeal(meal)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update meal ${meal.id}", e)
            Result.failure(e)
        }
    }

    override suspend fun setMealValid(id: Int, isValid: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mealDao.setValid(id, isValid)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set meal $id valid=$isValid", e)
            Result.failure(e)
        }
    }

    override suspend fun updateMealImage(id: Int, imageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            mealDao.updateImageId(id, imageId)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set image for meal $id", e)
            Result.failure(e)
        }
    }

    override suspend fun existsImportedAt(mealTimeUtc: Long, carbohydrates: Int): Boolean =
        withContext(Dispatchers.IO) { mealDao.countImportedAt(mealTimeUtc, carbohydrates) > 0 }

    override suspend fun existsBySourceId(sourceId: String): Boolean =
        withContext(Dispatchers.IO) { mealDao.countBySourceId(sourceId) > 0 }

    override suspend fun getMealsByUserId(userId: Int): Result<List<MealEntity>> {
        return try {
            val meals = mealDao.getMealsByUserId(userId)
            Result.success(meals)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load meals for userId=$userId", e)
            Result.failure(e)
        }
    }

    override fun observeMealsByUserId(userId: Int): Flow<List<MealEntity>> {
        return mealDao.observeMealsByUserId(userId)
    }

    override fun getAllMealsSince(startTime: Long, userId: Int): Flow<List<MealEntity>> {
        return mealDao.getAllMealsSince(startTime, userId)
    }
}
