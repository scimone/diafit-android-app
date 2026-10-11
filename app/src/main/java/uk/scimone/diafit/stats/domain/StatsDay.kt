package uk.scimone.diafit.stats.domain

import java.time.LocalDate
import kotlin.math.sqrt

/** A stretch of one sleep stage. */
data class SleepSpan(val startUtc: Long, val endUtc: Long, val stage: uk.scimone.diafit.core.domain.model.SleepStage)

/** One night, attributed to the day it ended on. Times of day are minutes after 18:00 of the evening before. */
data class SleepNight(
    val deepMs: Long,
    val remMs: Long,
    /** Light sleep, plus sleep whose stage the source doesn't tell. */
    val lightMs: Long,
    val awakeMs: Long,
    val bedMinutes: Int,
    val wakeMinutes: Int,
    /** Bedtime and wake-up of the main session (epoch ms) and its stages in time order, for the one-night timeline. */
    val startUtc: Long = 0,
    val endUtc: Long = 0,
    val spans: List<SleepSpan> = emptyList()
) {
    val asleepMs: Long get() = deepMs + remMs + lightMs
    val hasStages: Boolean get() = deepMs + remMs > 0

    companion object {
        /** The clock time (minutes after midnight) at which the schedule axis starts: 18:00. */
        const val AXIS_START_CLOCK_MIN = 18 * 60

        fun clockToAxis(clockMinutes: Int): Int = (clockMinutes - AXIS_START_CLOCK_MIN).let { if (it < 0) it + 24 * 60 else it }
        fun axisToClock(axisMinutes: Int): Int = (axisMinutes + AXIS_START_CLOCK_MIN) % (24 * 60)
    }
}

/** One day's CGM numbers (time-weighted by reading count, which is uniform within a source). */
data class GlucoseDay(
    val readingCount: Int,
    val meanMgdl: Double,
    val sdMgdl: Double,
    /** Shares 0..1. */
    val belowShare: Double,
    val inRangeShare: Double,
    val aboveShare: Double,
    /** Share 0..1 of the readings the sensor should have delivered (so far, for today). */
    val coverage: Double = 1.0
) {
    val cvPercent: Double get() = if (meanMgdl > 0) sdMgdl / meanMgdl * 100 else 0.0
    /** Glucose Management Indicator, the A1c a CGM mean predicts (%). */
    val gmi: Double get() = gmiOf(meanMgdl)

    companion object {
        fun gmiOf(meanMgdl: Double) = 3.31 + 0.02392 * meanMgdl

        fun from(n: Int, sum: Long, sumSq: Long, below: Int, above: Int): GlucoseDay? {
            if (n <= 0) return null
            val mean = sum.toDouble() / n
            val sd = sqrt((sumSq.toDouble() / n - mean * mean).coerceAtLeast(0.0))
            return GlucoseDay(n, mean, sd, below.toDouble() / n, (n - below - above).toDouble() / n, above.toDouble() / n)
        }
    }
}

/**
 * Everything the Stats cards need for one local day. A null field means "no data that day", which keeps a
 * day without a step count out of the averages instead of dragging them to zero.
 */
data class StatsDay(
    val date: LocalDate,
    val steps: Int? = null,
    val avgBpm: Double? = null,
    val restingBpm: Double? = null,
    val hrvMs: Double? = null,
    val sleep: SleepNight? = null,
    /** Minutes of raised activity (found in heart rate and steps) plus logged workouts, overlap counted once. */
    val activeMinutes: Double? = null,
    val totalKcal: Double? = null,
    val activeKcal: Double? = null,
    val carbs: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val mealKcal: Double? = null,
    val glucose: GlucoseDay? = null,
    /** All bolus insulin of the day, automatic microboluses included. */
    val bolusUnits: Double? = null,
    val basalUnits: Double? = null
) {
    /** Bolus and basal show up together, as long as one of them is above zero. */
    val hasInsulin: Boolean get() = (bolusUnits ?: 0.0) > 0.0 || (basalUnits ?: 0.0) > 0.0
}
