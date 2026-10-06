package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealPhoto
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import uk.scimone.diafit.core.domain.repository.MealRepository

/**
 * Saves edits to an existing course. [photos] is the complete, ordered photo list after editing
 * (the first becomes the cover); photo files the course no longer uses are deleted once the row is updated.
 */
class UpdateMealUseCase(
    private val mealRepository: MealRepository,
    private val fileStorageRepository: FileStorageRepository
) {
    suspend operator fun invoke(meal: MealEntity, photos: List<MealPhoto>): Result<Unit> {
        val previous = mealRepository.getMealById(meal.id)?.photoIds.orEmpty()
        for (photo in photos) {
            fileStorageRepository.storeImage(photo.imageId, photo.uri).onFailure { return Result.failure(it) }
        }
        val ids = photos.map { it.imageId }
        val updated = meal.copy(imageId = ids.firstOrNull().orEmpty(), extraImageIds = ids.drop(1))
        return mealRepository.updateMeal(updated).onSuccess {
            (previous - ids.toSet()).forEach { fileStorageRepository.deleteImage(it) }
        }
    }
}
