package uk.scimone.diafit.patterns.domain

import androidx.annotation.StringRes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uk.scimone.diafit.R
import uk.scimone.diafit.core.presentation.AppStrings

/**
 * Text a detector produced, stored as a string-resource name plus its arguments, so a pattern is shown in the
 * language the app has when it is opened rather than the one it was found in. Arguments are plain strings (already
 * formatted numbers, clock times); one that starts with "@" names another string resource ([ref]). For a plurals
 * resource the first argument is the count.
 */
@Serializable
data class Msg(val k: String, val a: List<String> = emptyList()) {
    fun text(): String = AppStrings.byName(k, a)
}

fun msg(key: String, vararg args: Any): Msg = Msg(key, args.map { it.toString() })

/** An argument that is itself a string resource ("evening" in "Low glucose in the evening"). */
fun ref(key: String): String = "@$key"

/** The five MVP pattern families (multivariable "combined" patterns are a later phase). */
@Serializable
enum class PatternCategory(@StringRes private val labelRes: Int) {
    TIME_OF_DAY(R.string.pat_cat_time_of_day), MEAL(R.string.pat_cat_meal), EXERCISE(R.string.pat_cat_exercise),
    SLEEP(R.string.pat_cat_sleep), TREND(R.string.pat_cat_trend), COMBINED(R.string.pat_cat_combined);

    val label: String get() = AppStrings.get(labelRes)
}

/** Whether the pattern is something to improve, to keep, or neither. */
@Serializable
enum class PatternTone { UNFAVORABLE, MIXED, FAVORABLE }

/** Lifecycle of a stored pattern (see [PatternLifecycle]). */
@Serializable
enum class PatternState(@StringRes private val labelRes: Int) {
    NEW(R.string.pat_state_new), UPDATED(R.string.pat_state_updated), ONGOING(R.string.pat_state_ongoing),
    RESOLVED(R.string.pat_state_resolved), DISMISSED(R.string.pat_state_dismissed);

    val label: String get() = AppStrings.get(labelRes)
}

/** Hours of the day a pattern lives in; `startHour > endHour` wraps midnight. */
@Serializable
data class HourSpan(val startHour: Int, val endHour: Int) {
    val label: String get() = "%02d:00–%02d:00".format(startHour, endHour % 24)
}

/** The real visualization behind an pattern, computed from the observations that produced it. */
@Serializable
sealed interface MiniGraph {
    /** An AGP (10/25/50/75/90th percentile by time of day, mg/dL) with [highlights] marked. */
    @Serializable
    @SerialName("agp")
    data class AgpWindow(
        val p10: List<Int>, val p25: List<Int>, val p50: List<Int>, val p75: List<Int>, val p90: List<Int>,
        val highlights: List<HourSpan> = emptyList()
    ) : MiniGraph

    /**
     * Glucose around an event (a meal, a workout), aligned on its start: [median] with a 25-75 % band
     * ([p25]/[p75]) for the main group, optionally the median of a [compare] group (dashed). Values are the change in
     * mg/dL from each event's own starting level, so events that start at different levels line up. Points are
     * [stepMinutes] apart from [startMinute] (negative = before the event). [spanMinutes] shades how long the event
     * itself lasted; [axis] are the minutes labelled under the plot (0 is named [eventLabel]).
     * Serialized as "meal" for compatibility with cards stored before workouts used it too.
     */
    @Serializable
    @SerialName("meal")
    data class EventResponse(
        val startMinute: Int, val stepMinutes: Int,
        val median: List<Int>, val p25: List<Int>, val p75: List<Int>,
        val aLabel: Msg,
        val compare: List<Int>? = null, val bLabel: Msg? = null,
        val eventLabel: Msg = Msg(EVENT_MEAL),
        val spanMinutes: Int? = null,
        val axis: List<Int> = listOf(0, 60, 120, 180, 240)
    ) : MiniGraph

    /** Two median profiles by time of day (mg/dL), e.g. weekdays vs weekends. */
    @Serializable
    @SerialName("compare")
    data class ProfileComparison(val aLabel: Msg, val a: List<Int>, val bLabel: Msg, val b: List<Int>) : MiniGraph
}

/**
 * One real occurrence behind a pattern, drawn on the detail page with the person's own glucose, meals, insulin,
 * activity and sleep (loaded when the page opens). [fromUtc]..[toUtc] is the stretch drawn; [focusFromUtc]..[focusToUtc]
 * is the part the pattern is about (shaded). [eventUtc] anchors the event the pattern is aligned on (a meal, the start of
 * an activity, a night), lasting until [eventEndUtc] for activity and sleep, named [eventLabel]. [markUtc] is the key
 * moment (the lowest point, the peak). [note] states the one number that makes this an example ("Lowest 58 mg/dL at
 * 03:10"). For comparisons, [group] is the graph's group label (aLabel or bLabel) this example belongs to.
 */
@Serializable
data class PatternExample(
    val fromUtc: Long,
    val toUtc: Long,
    val focusFromUtc: Long,
    val focusToUtc: Long,
    val note: Msg,
    val eventUtc: Long? = null,
    val eventEndUtc: Long? = null,
    val eventLabel: Msg? = null,
    val markUtc: Long? = null,
    val group: Msg? = null
)

/**
 * The pattern card contract: everything the list card and the detail view show. [tips] are exactly three once the
 * card has been through [PatternPipeline] (detectors emit it empty).
 */
@Serializable
data class PatternCard(
    /** Stable between scans ("tod.low.00-08"), so repeats update instead of duplicating. */
    val id: String,
    val category: PatternCategory,
    /** The detector that found it ("low", "high", "dawn", ...). */
    val detector: String,
    val title: Msg,
    val summary: Msg,
    val miniGraph: MiniGraph,
    val observationFromUtc: Long,
    val observationToUtc: Long,
    val baselineFromUtc: Long? = null,
    val baselineToUtc: Long? = null,
    val baselineLabel: Msg? = null,
    val sampleSize: Int,
    /** "days", "nights", "meals", "workouts" (see [SampleUnit]). */
    val sampleUnit: String,
    /** How many of the [sampleSize] observations showed the pattern, when that is a meaningful count. */
    val positiveCount: Int? = null,
    /** Comparable size of the effect, used to judge material change (a fraction, percentage points, mg/dL...). */
    val effectSize: Double,
    val effectLabel: Msg,
    /** The effect label only restates "[positiveCount] of [sampleSize] <unit>", which the detail page shows as a figure anyway. */
    val effectIsCount: Boolean = false,
    val uncertainty: Msg,
    val limitations: List<Msg>,
    /** The days (or meals, workouts, nights) the detail page draws to show the pattern on the person's own data. */
    val cases: List<PatternExample> = emptyList(),
    /** String-resource names of the three tips (see [PatternTips]). */
    val tips: List<String> = emptyList(),
    val tone: PatternTone,
    val priority: Double,
    val detailRoute: String = "pattern/$id"
)

/** Resource-name suffixes of the units a sample is counted in: `pat_unit_<unit>` is the plural noun. */
object SampleUnit {
    const val DAYS = "days"; const val NIGHTS = "nights"; const val MEALS = "meals"; const val WORKOUTS = "workouts"
    const val ACTIVITY = "activity"
    const val ACTIVITIES = "activities"; const val DINNERS = "dinners"
}

const val EVENT_MEAL = "pat_ev_meal"
const val EVENT_START = "pat_ev_start"

/** Which tip set a detector's finding uses (see [PatternTips]). */
enum class TipTopic(val isLow: Boolean = false) {
    LOW_NIGHT(true), LOW_DAY(true), HIGH_NIGHT, HIGH_DAY, VARIABILITY, DAWN,
    STEADY_NIGHT, STEADY_DAY, WEEKEND_BETTER, WEEKEND_WORSE,
    MEAL_LOW(true), MEAL_SPIKE, MEAL_SLOW, MEAL_GOOD, MEAL_RICH, MEAL_TYPE, MEAL_BOLUS,
    EX_DROP, EX_LOW_NOW(true), EX_LOW_LATER(true), EX_RISE, EX_GOOD, EX_LONG, EX_EVENING, EX_NEXTDAY, EX_INTENSITY,
    SLEEP_SHORT, SLEEP_RESTLESS, SLEEP_IRREGULAR,
    TREND_BETTER, TREND_WORSE, TREND_LOWS_UP(true),
    COMBO_LATE_DINNER, COMBO_EVENING_LOW(true), COMBO_SLEEP_BREAKFAST,
    CTX_EX_PRE, CTX_EX_GAP, CTX_EX_TYPE, CTX_MEAL_DELAY, CTX_MEAL_WORSE, CTX_MEAL_BETTER, CTX_NIGHT_BETTER, CTX_NIGHT_WORSE,
    CTX_SLEEP_ACTIVITY, CTX_SLEEP_GOOD, CTX_LATE_RICH
}

/** A detector's raw finding: the card minus its tips, plus what is needed to choose them. */
data class PatternCandidate(val card: PatternCard, val topic: TipTopic, val escalate: Boolean = false)

/** An pattern as persisted, with its lifecycle bookkeeping. */
data class StoredPattern(
    val card: PatternCard,
    val state: PatternState,
    /** Opened by the user since it last became new/updated. */
    val isRead: Boolean,
    val firstSeenUtc: Long,
    val lastSeenUtc: Long,
    /** Consecutive scans in which the pattern was not found. */
    val missedScans: Int = 0,
    /** Effect size when the pattern last became new/updated: the yardstick for "material change". */
    val referenceEffect: Double,
    /** When the push was sent for the current new/updated episode; null = not yet. */
    val notifiedAtUtc: Long? = null
) {
    /** Counts toward the badge. */
    val isUnreviewed: Boolean get() = !isRead && (state == PatternState.NEW || state == PatternState.UPDATED)
}
