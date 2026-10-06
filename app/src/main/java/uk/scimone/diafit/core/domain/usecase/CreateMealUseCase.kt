package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealPhoto
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import java.time.Instant
import java.util.UUID

/** Logs a course. Pass the [sittingId] of an existing meal to add it as another course of that meal. */
class CreateMealUseCase(
    private val mealRepository: MealRepository,
    private val fileStorageRepository: FileStorageRepository
) {
    suspend operator fun invoke(
        photos: List<MealPhoto>,
        description: String?,
        userId: Int,
        sittingId: String? = null,
        mealTimeUtc: Long = Instant.now().toEpochMilli(),
        calories: Int? = null,
        carbohydrates: Int = 0,
        proteins: Int? = null,
        fats: Int? = null,
        impactType: ImpactType = ImpactType.MEDIUM,
        mealType: MealType = MealType.SNACK,
        recommendation: String? = null,
        reasoning: String? = null,
    ): Result<MealEntity> {
        // Photos are optional: a meal can be logged from carbs alone.
        for (photo in photos) {
            fileStorageRepository.storeImage(photo.imageId, photo.uri).onFailure { return Result.failure(it) }
        }
        val ids = photos.map { it.imageId }

        val meal = MealEntity(
            userId = userId,
            description = description,
            createdAtUtc = Instant.now().toEpochMilli(),
            mealTimeUtc = mealTimeUtc,
            calories = calories,
            carbohydrates = carbohydrates,
            proteins = proteins,
            fats = fats,
            impactType = impactType,
            mealType = mealType,
            isValid = true,
            imageId = ids.firstOrNull().orEmpty(),
            extraImageIds = ids.drop(1),
            recommendation = recommendation,
            reasoning = reasoning,
            // Every logged meal gets a sitting so further courses can join it later.
            sittingId = sittingId ?: UUID.randomUUID().toString()
        )
        return mealRepository.createMeal(meal).map { meal }
    }
}
