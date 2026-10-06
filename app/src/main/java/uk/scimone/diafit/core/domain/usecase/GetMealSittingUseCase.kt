package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.model.MealSitting
import uk.scimone.diafit.core.domain.model.SITTING_OPEN_WINDOW_MS
import uk.scimone.diafit.core.domain.repository.MealRepository

/** The meal (all courses) a course belongs to. */
class GetMealSittingUseCase(private val mealRepository: MealRepository) {
    suspend operator fun invoke(mealId: Int): MealSitting? {
        val meal = mealRepository.getMealById(mealId)?.takeIf { it.isValid } ?: return null
        val courses = meal.sittingId?.let { mealRepository.getMealsBySitting(it) }?.takeIf { it.isNotEmpty() }
        return MealSitting(courses ?: listOf(meal))
    }
}

/**
 * The meal still in progress, if any: the newest logged meal whose latest course is less than
 * [SITTING_OPEN_WINDOW_MS] old. Offered as "add a course" so a long dinner stays one meal.
 */
class GetOpenSittingUseCase(
    private val mealRepository: MealRepository,
    private val getMealSitting: GetMealSittingUseCase
) {
    suspend operator fun invoke(userId: Int, now: Long = System.currentTimeMillis()): MealSitting? {
        val latest = mealRepository.getLatestLoggedMeal(userId, now) ?: return null
        val sitting = getMealSitting(latest.id) ?: return null
        return sitting.takeIf { now - it.endTime <= SITTING_OPEN_WINDOW_MS }
    }
}
