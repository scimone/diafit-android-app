package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealSitting
import uk.scimone.diafit.core.domain.repository.CgmRepository

class CalculateMealGlucoseImpactUseCase(
    private val cgmRepository: CgmRepository,
    private val userId: Int
) {

    data class Result(
        val timeInRange: Double,
        val timeAboveRange: Double,
        val timeBelowRange: Double,
        /** Share (0..1) of the window's 5-minute slots that have at least one reading. */
        val coverage: Double = 0.0
    )

    suspend operator fun invoke(meal: MealEntity, targetRange: GlucoseTargetRange): Result {
        val start = meal.mealTimeUtc
        return invoke(start, start + meal.impactType.durationMinutes * 60_000L, targetRange)
    }

    /** Over a whole meal: from its first course until the last one has been absorbed. */
    suspend operator fun invoke(sitting: MealSitting, targetRange: GlucoseTargetRange): Result =
        invoke(sitting.startTime, sitting.effectEndTime, targetRange)

    suspend operator fun invoke(start: Long, end: Long, targetRange: GlucoseTargetRange): Result {
        val entries = cgmRepository.getEntriesBetween(start, end, userId)
        if (entries.isEmpty()) return Result(0.0, 0.0, 0.0)

        val totalCount = entries.size.toDouble()
        val slotMs = 5 * 60_000L
        val slots = entries.map { (it.timestamp - start) / slotMs }.toSet().size
        val coverage = (slots / (((end - start) / slotMs).toDouble().coerceAtLeast(1.0))).coerceAtMost(1.0)

        val timeInRange = entries.count { it.isInRange(targetRange) } / totalCount * 100
        val timeAboveRange = entries.count { it.isAboveRange(targetRange) } / totalCount * 100
        val timeBelowRange = entries.count { it.isBelowRange(targetRange) } / totalCount * 100

        return Result(
            timeInRange = timeInRange,
            timeAboveRange = timeAboveRange,
            timeBelowRange = timeBelowRange,
            coverage = coverage
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

