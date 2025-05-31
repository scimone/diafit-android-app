package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
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

    suspend operator fun invoke(meal: MealEntity, targetRange: GlucoseTargetRange): Result {
        val impactDurationMinutes = meal.impactType.durationMinutes
        val start = meal.mealTimeUtc
        val end = start + impactDurationMinutes * 60 * 1000 // convert minutes to millis

        val entries = cgmRepository.getEntriesBetween(start, end, userId)
        if (entries.isEmpty()) return Result(0.0, 0.0, 0.0)

        val totalCount = entries.size.toDouble()

        val timeInRange = entries.count { it.isInRange(targetRange) } / totalCount * 100
        val timeAboveRange = entries.count { it.isAboveRange(targetRange) } / totalCount * 100
        val timeBelowRange = entries.count { it.isBelowRange(targetRange) } / totalCount * 100

        return Result(
            timeInRange = timeInRange,
            timeAboveRange = timeAboveRange,
            timeBelowRange = timeBelowRange
        )
    }

    // Helper extensions now take targetRange as parameter
    private fun CgmEntity.isInRange(targetRange: GlucoseTargetRange): Boolean =
        valueMgdl in targetRange.lowerBound..targetRange.upperBound

    private fun CgmEntity.isAboveRange(targetRange: GlucoseTargetRange): Boolean =
        valueMgdl > targetRange.upperBound

    private fun CgmEntity.isBelowRange(targetRange: GlucoseTargetRange): Boolean =
        valueMgdl < targetRange.lowerBound
}

