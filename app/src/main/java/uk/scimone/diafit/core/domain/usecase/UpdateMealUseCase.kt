package uk.scimone.diafit.core.domain.usecase

import android.net.Uri
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import uk.scimone.diafit.core.domain.repository.MealRepository

/**
 * Saves edits to an existing meal. [newImageUri]/[newImageId] are set only when the photo was
 * replaced or added, or [removeImage] when it was dropped; `meal.imageId` is the currently stored photo;
 * the previous photo file is removed once the row has been updated.
 */
class UpdateMealUseCase(
    private val mealRepository: MealRepository,
    private val fileStorageRepository: FileStorageRepository
) {
    suspend operator fun invoke(
        meal: MealEntity,
        newImageUri: Uri? = null,
        newImageId: String? = null,
        removeImage: Boolean = false
    ): Result<Unit> {
        var updated = meal
        var oldImageId: String? = null
        if (newImageUri != null && newImageId != null) {
            fileStorageRepository.storeImage(newImageId, newImageUri)
                .onFailure { return Result.failure(it) }
            oldImageId = meal.imageId
            updated = meal.copy(imageId = newImageId)
        }
        if (removeImage) {
            oldImageId = meal.imageId
            updated = meal.copy(imageId = "")
        }
        return mealRepository.updateMeal(updated).onSuccess {
            oldImageId?.let { fileStorageRepository.deleteImage(it) }
        }
    }
}
