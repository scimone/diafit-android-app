package uk.scimone.diafit.core.domain.usecase

import android.net.Uri
import android.util.Log
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.repository.MealRepository
import java.time.Instant
import java.util.UUID

class CreateMealUseCase(
    private val mealRepository: MealRepository
) {
    suspend operator fun invoke(
        imageUri: Uri,
        description: String?,
        userId: Int,
        imageId: String, // <-- ADD THIS!
        mealTimeUtc: Long = Instant.now().toEpochMilli(),
        calories: Int? = null,
        carbohydrates: Int = 0,
        proteins: Int? = null,
        fats: Int? = null
    ): Result<Pair<MealEntity, Uri>> {
        val storedImageUriResult = mealRepository.storeImage(imageId, imageUri)
        if (storedImageUriResult.isFailure) return Result.failure(storedImageUriResult.exceptionOrNull()!!)

        val storedUri = storedImageUriResult.getOrThrow()

        val meal = MealEntity(
            userId = userId,
            description = description,
            createdAtUtc = Instant.now().toEpochMilli(),
            mealTimeUtc = mealTimeUtc,
            calories = calories,
            carbohydrates = carbohydrates,
            proteins = proteins,
            fats = fats,
            isValid = true,
            imageId = imageId,
            recommendation = null,
            reasoning = null
        )

        val createResult = mealRepository.createMeal(meal)
        if (createResult.isFailure) return Result.failure(createResult.exceptionOrNull()!!)

        return Result.success(Pair(meal, storedUri))

    }
}