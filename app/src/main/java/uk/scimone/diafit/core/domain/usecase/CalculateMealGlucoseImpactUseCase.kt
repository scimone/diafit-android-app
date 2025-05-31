package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.repository.CgmRepository

class CalculateMealGlucoseImpactUseCase(
    private val cgmRepository: CgmRepository,
    private val userId: Int
) {

    data class Result(
        val timeInRange: Double,
        val timeAboveRange: Double,
        val timeBelowRange: Double
    )

    suspend operator fun invoke(meal: MealEntity): Result {
        val impactDurationMinutes = meal.impactType.durationMinutes
        val start = meal.mealTimeUtc
        val end = start + impactDurationMinutes * 60 * 1000 // convert minutes to millis

        val entries = cgmRepository.getEntriesBetween(start, end, userId)
        if (entries.isEmpty()) return Result(0.0, 0.0, 0.0)

        val totalCount = entries.size.toDouble()
        val timeInRange = entries.count { it.isInRange() } / totalCount * 100
        val timeAboveRange = entries.count { it.isAboveRange() } / totalCount * 100
        val timeBelowRange = entries.count { it.isBelowRange() } / totalCount * 100

        return Result(
            timeInRange = timeInRange,
            timeAboveRange = timeAboveRange,
            timeBelowRange = timeBelowRange
        )
    }

    // Helper extensions, define these based on your CGM target ranges
    private fun CgmEntity.isInRange(): Boolean = valueMgdl in 70..180
    private fun CgmEntity.isAboveRange(): Boolean = valueMgdl > 180
    private fun CgmEntity.isBelowRange(): Boolean = valueMgdl < 70
}
