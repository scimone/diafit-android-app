package uk.scimone.diafit.core.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.MealEntity

interface MealRepository {

    suspend fun createMeal(meal: MealEntity): Result<Unit>

//    suspend fun getMealById(id: UUID): Result<MealEntity?>
//
//    suspend fun getMealsForUser(userId: UUID): Result<List<MealEntity>>
//
//    suspend fun updateMeal(meal: MealEntity): Result<Unit>
//
//    suspend fun deleteMeal(id: UUID): Result<Unit>

    suspend fun storeImage(mealId: String, sourceUri: Uri): Result<Uri>

    suspend fun getMealsByUserId(userId: String): Result<List<MealEntity>>

    fun observeMealsByUserId(userId: String): Flow<List<MealEntity>>


}
