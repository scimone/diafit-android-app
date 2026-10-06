package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.repository.MealRepository

/** Soft-deletes (invalidates) or restores meals; the rows and photos are kept so a delete can be undone. */
class SetMealValidUseCase(private val mealRepository: MealRepository) {
    suspend operator fun invoke(mealId: Int, isValid: Boolean): Result<Unit> =
        mealRepository.setMealValid(mealId, isValid)

    /** Every course of a meal at once. */
    suspend operator fun invoke(mealIds: List<Int>, isValid: Boolean): Result<Unit> =
        mealRepository.setMealsValid(mealIds, isValid)
}
