package uk.scimone.diafit.core.domain.model

import kotlin.math.abs

/** Glucose is judged over this long after a meal starts (Journal, History). */
const val MEAL_OUTCOME_WINDOW_MS = 4 * 60 * 60_000L

/** Below this share of 5-minute slots with a reading, a meal's outcome isn't shown. */
const val MEAL_OUTCOME_MIN_COVERAGE = 0.7

/** Insulin this long before the first course / after the last one counts as dosed for the meal. */
const val MEAL_DOSE_LEAD_MS = 30 * 60_000L
const val MEAL_DOSE_TAIL_MS = 30 * 60_000L

private const val SLOT_MS = 5 * 60_000L
private const val START_MATCH_MS = 15 * 60_000L

/** What happened around one meal: insulin for it and glucose over the [MEAL_OUTCOME_WINDOW_MS] after it. */
data class MealOutcome(
    val timeInRange: Double,
    val timeAboveRange: Double,
    val timeBelowRange: Double,
    val status: Status,
    /** Insulin from [MEAL_DOSE_LEAD_MS] before the first course to [MEAL_DOSE_TAIL_MS] after the last. */
    val insulinUnits: Double,
    /** Reading closest to the meal start (within 15 min). */
    val startMgdl: Int?,
    /** Highest reading between the meal start and the end of its absorption. */
    val peakMgdl: Int?,
    val peakTimeUtc: Long?
) {
    enum class Status { READY, TOO_EARLY, NOT_ENOUGH_DATA }

    companion object {
        /** [readings] and [boluses] must cover the meal's dose window and outcome window. */
        fun of(
            sitting: MealSitting,
            readings: List<CgmEntity>,
            boluses: List<BolusEntity>,
            target: GlucoseTargetRange,
            now: Long = System.currentTimeMillis()
        ): MealOutcome {
            val start = sitting.startTime
            val end = start + MEAL_OUTCOME_WINDOW_MS
            val window = readings.filter { it.timestamp in start..end }
            val coverage = window.map { (it.timestamp - start) / SLOT_MS }.toSet().size / (MEAL_OUTCOME_WINDOW_MS / SLOT_MS).toDouble()
            val n = window.size.toDouble().coerceAtLeast(1.0)
            // The peak is looked for while the food is absorbing (as on the meal page), not over the
            // whole outcome window, where a later, unrelated rise could be picked up.
            val peak = window.filter { it.timestamp <= sitting.effectEndTime }.maxByOrNull { it.valueMgdl }
            return MealOutcome(
                timeInRange = window.count { it.valueMgdl in target.lowerBound..target.upperBound } / n * 100,
                timeAboveRange = window.count { it.valueMgdl > target.upperBound } / n * 100,
                timeBelowRange = window.count { it.valueMgdl < target.lowerBound } / n * 100,
                status = when {
                    now < end -> Status.TOO_EARLY
                    coverage < MEAL_OUTCOME_MIN_COVERAGE -> Status.NOT_ENOUGH_DATA
                    else -> Status.READY
                },
                insulinUnits = boluses
                    .filter { it.timestampUtc in (start - MEAL_DOSE_LEAD_MS)..(sitting.endTime + MEAL_DOSE_TAIL_MS) }
                    .sumOf { it.value.toDouble() },
                startMgdl = readings.filter { abs(it.timestamp - start) <= START_MATCH_MS }
                    .minByOrNull { abs(it.timestamp - start) }?.valueMgdl,
                peakMgdl = peak?.valueMgdl,
                peakTimeUtc = peak?.timestamp
            )
        }
    }
}
