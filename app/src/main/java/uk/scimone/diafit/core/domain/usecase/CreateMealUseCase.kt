package uk.scimone.diafit.core.domain.usecase

import android.net.Uri
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.Nutrients
import uk.scimone.diafit.core.domain.repository.MealRepository
import java.time.Instant
import java.util.UUID

class CreateMealUseCase constructor(
    private val mealRepository: MealRepository
) {
    /**
     * Creates and saves a new meal with image and metadata.
     *
     * @param imageUri Uri pointing to the selected or captured image.
     * @param description Optional description of the meal.
     * @param userId String ID of the user.
     * @param mealTimeUtc When the meal was eaten, defaults to now.
     * @param calories Optional calorie count for the meal.
     * @param carbohydrates Carbohydrate content in grams, defaults to 0.
     * @param proteins Optional protein content in grams.
     * @param fats Optional fat content in grams.
     *
     * @return The saved MealEntity
     */
    suspend operator fun invoke(
        imageUri: Uri,
        description: String?,
        userId: String,
        mealTimeUtc: Long = Instant.now().toEpochMilli(),
        calories: Int? = null,
        carbohydrates: Int = 0,
        proteins: Int? = null,
        fats: Int? = null
    ): MealEntity {
        val mealId = UUID.randomUUID().toString()

        // Save image locally and get stored Uri/path from repository
        val storedImageUri = mealRepository.storeImage(mealId, imageUri)

        val meal = MealEntity(
            id = mealId,
            userId = userId,
            description = description,
            createdAtUtc = Instant.now().toEpochMilli(),
            mealTimeUtc = mealTimeUtc,
            calories = calories,
            carbohydrates = carbohydrates,
            proteins = proteins,
            fats = fats,
            isValid = true,
            imageId = mealId,
            recommendation = null,
            reasoning = null
        )

        mealRepository.createMeal(meal)

        return meal
    }
}
