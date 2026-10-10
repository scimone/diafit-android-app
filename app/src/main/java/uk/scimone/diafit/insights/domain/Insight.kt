package uk.scimone.diafit.insights.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The five MVP pattern families (multivariable "combined" patterns are a later phase). */
@Serializable
enum class InsightCategory(val label: String) {
    TIME_OF_DAY("Time of day"), MEAL("Meals"), EXERCISE("Activity"), SLEEP("Sleep"), TREND("Trends")
}

/** Whether the pattern is something to improve, to keep, or neither. */
@Serializable
enum class InsightTone { UNFAVORABLE, MIXED, FAVORABLE }

/** Lifecycle of a stored insight (see [InsightLifecycle]). */
@Serializable
enum class InsightState(val label: String) {
    NEW("New"), UPDATED("Updated"), ONGOING("Ongoing"), RESOLVED("No longer observed"), DISMISSED("Dismissed")
}

/** Hours of the day a pattern lives in; `startHour > endHour` wraps midnight. */
@Serializable
data class HourSpan(val startHour: Int, val endHour: Int) {
    val label: String get() = "%02d:00–%02d:00".format(startHour, endHour % 24)
}

/** The real visualization behind an insight, computed from the observations that produced it. */
@Serializable
sealed interface MiniGraph {
    /** An AGP (10/25/50/75/90th percentile by time of day, mg/dL) with [highlights] marked. */
    @Serializable
    @SerialName("agp")
    data class AgpWindow(
        val p10: List<Int>, val p25: List<Int>, val p50: List<Int>, val p75: List<Int>, val p90: List<Int>,
        val highlights: List<HourSpan> = emptyList()
    ) : MiniGraph

    /** Two median profiles by time of day (mg/dL), e.g. weekdays vs weekends. */
    @Serializable
    @SerialName("compare")
    data class ProfileComparison(val aLabel: String, val a: List<Int>, val bLabel: String, val b: List<Int>) : MiniGraph
}

/**
 * The insight card contract: everything the list card and the detail view show. [tips] are exactly three once the
 * card has been through [InsightPipeline] (detectors emit it empty).
 */
@Serializable
data class InsightCard(
    /** Stable between scans ("tod.low.00-08"), so repeats update instead of duplicating. */
    val id: String,
    val category: InsightCategory,
    /** The detector that found it ("low", "high", "dawn", ...). */
    val detector: String,
    val title: String,
    val summary: String,
    val miniGraph: MiniGraph,
    val observationFromUtc: Long,
    val observationToUtc: Long,
    val baselineFromUtc: Long? = null,
    val baselineToUtc: Long? = null,
    val baselineLabel: String? = null,
    val sampleSize: Int,
    /** "days", "nights", "meals", "workouts". */
    val sampleUnit: String,
    /** How many of the [sampleSize] observations showed the pattern, when that is a meaningful count. */
    val positiveCount: Int? = null,
    /** Comparable size of the effect, used to judge material change (a fraction, percentage points, mg/dL...). */
    val effectSize: Double,
    val effectLabel: String,
    val uncertainty: String,
    val limitations: List<String>,
    val examples: List<String> = emptyList(),
    val tips: List<String> = emptyList(),
    val tone: InsightTone,
    val priority: Double,
    val detailRoute: String = "insight/$id"
)

/** Which tip set a detector's finding uses (see [InsightTips]). */
enum class TipTopic(val isLow: Boolean = false) {
    LOW_NIGHT(true), LOW_DAY(true), HIGH_NIGHT, HIGH_DAY, VARIABILITY, DAWN,
    STEADY_NIGHT, STEADY_DAY, WEEKEND_BETTER, WEEKEND_WORSE
}

/** A detector's raw finding: the card minus its tips, plus what is needed to choose them. */
data class InsightCandidate(val card: InsightCard, val topic: TipTopic, val escalate: Boolean = false)

/** An insight as persisted, with its lifecycle bookkeeping. */
data class StoredInsight(
    val card: InsightCard,
    val state: InsightState,
    /** Opened by the user since it last became new/updated. */
    val isRead: Boolean,
    val firstSeenUtc: Long,
    val lastSeenUtc: Long,
    /** Consecutive scans in which the pattern was not found. */
    val missedScans: Int = 0,
    /** Effect size when the insight last became new/updated: the yardstick for "material change". */
    val referenceEffect: Double,
    /** When the push was sent for the current new/updated episode; null = not yet. */
    val notifiedAtUtc: Long? = null
) {
    /** Counts toward the badge. */
    val isUnreviewed: Boolean get() = !isRead && (state == InsightState.NEW || state == InsightState.UPDATED)
}
