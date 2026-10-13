package uk.scimone.diafit.dailyrecap.domain

import kotlinx.serialization.Serializable

/*
 * What the LLM is shown: one day, boiled down to a few hundred numbers (about 1.5k tokens) instead of raw readings.
 * Glucose is cut into the day's blocks and its out-of-range episodes, every meal is one line with its glucose
 * outcome, insulin and activity are totals. Times are local "HH:mm". Nothing that identifies the person is sent.
 */

@Serializable
data class RecapInput(
    val date: String,
    val weekday: String,
    /** Set when the day was still running: everything is "so far", up to this time. */
    val partialDayUntil: String? = null,
    val targetMgdl: String,
    val glucose: GlucoseSummary? = null,
    val vsPrevious7Days: Comparison? = null,
    val blocks: List<BlockSummary> = emptyList(),
    val episodes: List<EpisodeSummary> = emptyList(),
    val meals: List<MealSummary> = emptyList(),
    val insulin: InsulinSummary? = null,
    val activity: ActivitySummary? = null,
    /** Recurring patterns the app currently sees (titles only), so advice can build on what is known about this person. */
    val knownPatterns: List<String> = emptyList(),
    /** Sensor / pump parts that expire within a day. */
    val devicesDueSoon: List<String> = emptyList()
)

@Serializable
data class GlucoseSummary(
    val inRangePct: Int,
    val belowPct: Int,
    val veryLowPct: Int,
    val abovePct: Int,
    val veryHighPct: Int,
    val meanMgdl: Int,
    /** Coefficient of variation in %; at most 36 is considered stable. */
    val cvPct: Int,
    val lowest: String,
    val highest: String,
    val coveragePct: Int
)

@Serializable
data class Comparison(
    val inRangePctAvg: Int,
    val meanMgdlAvg: Int,
    val cvPctAvg: Int,
    val lowEpisodesPerDayAvg: Double,
    val highEpisodesPerDayAvg: Double,
    val daysWithData: Int,
    val inRangeDeltaPoints: Int
)

@Serializable
data class BlockSummary(val name: String, val inRangePct: Int, val meanMgdl: Int, val minMgdl: Int, val maxMgdl: Int)

@Serializable
data class EpisodeSummary(
    val kind: String,
    val from: String,
    val minutes: Int,
    /** Lowest value of a low, highest of a high. */
    val extremeMgdl: Int,
    val severe: Boolean,
    /** What happened just before, in plain words ("meal 12:30, 62 g carbs, 6.0 U", "workout ended 17:10"). */
    val before: List<String>
)

@Serializable
data class MealSummary(
    val time: String,
    val type: String,
    val name: String,
    val carbsG: Int,
    val proteinG: Int? = null,
    val fatG: Int? = null,
    val insulinU: Double,
    val glucoseAtStart: Int? = null,
    val peak: Int? = null,
    val peakAfterMin: Int? = null,
    /** Share of the 4 h after the meal spent in range; only when those 4 h are over and the data is complete. */
    val inRange4hPct: Int? = null
)

@Serializable
data class InsulinSummary(
    val mealAndCorrectionBolusU: Double,
    val autoBolusSmbU: Double,
    val basalU: Double? = null,
    val bolusCount: Int,
    val carbsTotalG: Int
)

@Serializable
data class ActivitySummary(
    val steps: Int? = null,
    val workouts: List<String> = emptyList(),
    val sleepBeforeTheDay: String? = null,
    val restingHeartRate: Int? = null
)
