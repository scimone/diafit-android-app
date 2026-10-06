package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.repository.MealRepository

/** Soft-deletes (invalidates) or restores a meal; the row and its photo are kept so a delete can be undone. */
class SetMealValidUseCase(private val mealRepository: MealRepository) {
    suspend operator fun invoke(mealId: Int, isValid: Boolean): Result<Unit> =
        mealRepository.setMealValid(mealId, isValid)
}
