package uk.scimone.diafit.stats.domain

import androidx.annotation.StringRes
import uk.scimone.diafit.R
import uk.scimone.diafit.core.presentation.AppStrings
import uk.scimone.diafit.core.presentation.formatLocalized

/**
 * What a W/M/Y card shows: one number per day. Everything is single-valued (a stacked quantity such as glucose zones
 * or macros is several metrics, each with its own card) except [SLEEP_SCHEDULE], whose two values are bedtime and
 * wake-up. [segments] is the value list of one day's bar, null when the day has no data.
 */
enum class StatsMetric(
    @StringRes val titleRes: Int,
    /** Whole days add up (steps, insulin...): the detail page also shows a total. Others only average. */
    val additive: Boolean = false,
    /** Shown only on these pages (A1c is meaningless over a week); null = every W/M/Y page. */
    val periods: Set<StatsPeriod>? = null
) {
    GLUCOSE_IN_RANGE(R.string.stats_m_in_range),
    GLUCOSE_BELOW(R.string.stats_m_below),
    GLUCOSE_ABOVE(R.string.stats_m_above),
    GLUCOSE_CV(R.string.stats_m_cv),
    GLUCOSE_A1C(R.string.stats_m_a1c, periods = setOf(StatsPeriod.MONTH, StatsPeriod.YEAR)),
    GLUCOSE_COVERAGE(R.string.stats_m_coverage),
    CARBS(R.string.macro_carbs, additive = true),
    PROTEIN(R.string.macro_protein, additive = true),
    FAT(R.string.macro_fat, additive = true),
    MEAL_CALORIES(R.string.stats_m_calories_eaten, additive = true),
    BOLUS(R.string.datatype_bolus, additive = true),
    BASAL(R.string.datatype_basal, additive = true),
    SLEEP(R.string.datatype_sleep),
    DEEP_SLEEP(R.string.stats_m_deep_sleep),
    REM_SLEEP(R.string.stats_m_rem_sleep),
    LIGHT_SLEEP(R.string.stats_m_light_sleep),
    SLEEP_SCHEDULE(R.string.stats_m_sleep_schedule),
    HEART_RATE(R.string.datatype_heart_rate),
    RESTING_HEART_RATE(R.string.stats_m_resting_hr),
    HEART_RATE_VARIABILITY(R.string.stats_m_hrv),
    CALORIES(R.string.stats_m_calories, additive = true),
    ACTIVE_CALORIES(R.string.stats_m_active_calories, additive = true),
    STEPS(R.string.datatype_steps, additive = true),
    ACTIVITY(R.string.stats_m_active_time, additive = true);

    val title: String get() = AppStrings.get(titleRes)

    /** The values of one day's bar, or null when the day has no data for this metric. */
    fun segments(day: StatsDay): List<Double>? {
        val v: Double? = when (this) {
            GLUCOSE_IN_RANGE -> day.glucose?.let { it.inRangeShare * 100 }
            // Very low / very high stacked on the low / high part.
            GLUCOSE_BELOW -> return day.glucose?.let { listOf(it.veryLowShare * 100, (it.belowShare - it.veryLowShare) * 100) }
            GLUCOSE_ABOVE -> return day.glucose?.let { listOf(it.veryHighShare * 100, (it.aboveShare - it.veryHighShare) * 100) }
            GLUCOSE_CV -> day.glucose?.cvPercent
            GLUCOSE_A1C -> day.glucose?.gmi
            GLUCOSE_COVERAGE -> day.glucose?.let { it.coverage * 100 }
            BOLUS -> if (day.hasInsulin) day.bolusUnits ?: 0.0 else null
            BASAL -> if (day.hasInsulin) day.basalUnits ?: 0.0 else null
            CARBS -> day.carbs
            PROTEIN -> day.protein?.takeIf { it > 0 }
            FAT -> day.fat?.takeIf { it > 0 }
            MEAL_CALORIES -> day.mealKcal?.takeIf { it > 0 }
            CALORIES -> day.totalKcal
            ACTIVE_CALORIES -> day.activeKcal
            STEPS -> day.steps?.toDouble()
            ACTIVITY -> day.activeMinutes
            HEART_RATE -> day.avgBpm
            RESTING_HEART_RATE -> day.restingBpm
            HEART_RATE_VARIABILITY -> day.hrvMs
            SLEEP -> day.sleep?.takeIf { it.asleepMs > 0 }?.let { it.asleepMs.hours }
            // Stage cards need a source that tells stages apart.
            DEEP_SLEEP -> day.sleep?.takeIf { it.hasStages }?.let { it.deepMs.hours }
            REM_SLEEP -> day.sleep?.takeIf { it.hasStages }?.let { it.remMs.hours }
            LIGHT_SLEEP -> day.sleep?.takeIf { it.hasStages }?.let { it.lightMs.hours }
            SLEEP_SCHEDULE -> return day.sleep?.takeIf { it.asleepMs > 0 }?.let { listOf(it.bedMinutes.toDouble(), it.wakeMinutes.toDouble()) }
        }
        return v?.let { listOf(it) }
    }

    /** The one number that stands for the day (for the schedule: bedtime). */
    fun primary(day: StatsDay): Double? = segments(day)?.let { if (stacked) it.sum() else it.first() }

    /** Metrics whose bar stacks several values that add up to the figure shown. */
    val stacked: Boolean get() = this == GLUCOSE_BELOW || this == GLUCOSE_ABOVE

    /** Mean of every value over the days of [days] that have data; null when none do. Used for a bar that spans several days. */
    fun barSegments(days: List<StatsDay>): List<Double>? {
        val all = days.mapNotNull(::segments)
        if (all.isEmpty()) return null
        return List(all.first().size) { i -> all.sumOf { it[i] } / all.size }
    }

    /** Mean of [primary] over the days with data. */
    fun average(days: List<StatsDay>): Double? = days.mapNotNull(::primary).takeIf { it.isNotEmpty() }?.average()

    fun total(days: List<StatsDay>): Double? = days.mapNotNull(::primary).takeIf { it.isNotEmpty() }?.sum()

    fun daysWithData(days: List<StatsDay>): Int = days.count { segments(it) != null }

    private val Long.hours: Double get() = this / 3_600_000.0
}

/** The cards of each page, in order: glucose first, then meals, insulin, sleep, heart rate, calories burned, then the rest. */
object StatsCards {
    /** Day page: one card per group (insulin = bolus + basal; activity = steps, active time, calories, heart rate). */
    enum class DayCard { GLUCOSE, MEALS, INSULIN, ACTIVITY, SLEEP }

    val day = DayCard.entries

    /** Week / month / year pages: one card per metric. */
    val bars: List<StatsMetric> = StatsMetric.entries

    fun bars(period: StatsPeriod): List<StatsMetric> = bars.filter { it.periods?.contains(period) ?: true }
}

/** The average of [metric] over each window in a row of consecutive periods, oldest first (the Trends chart). */
data class StatsTrendPoint(val label: String, val value: Double?, val isCurrent: Boolean)

/** Daily averages of [metric] for the buckets of a trend (one per period). */
fun trendPoints(metric: StatsMetric, days: List<StatsDay>, windows: List<StatsWindow>): List<StatsTrendPoint> =
    windows.mapIndexed { i, w ->
        val inWindow = days.filter { it.date >= w.first && it.date < w.lastExclusive }
        val label = when (w.period) {
            StatsPeriod.YEAR -> w.first.year.toString()
            StatsPeriod.MONTH -> w.first.formatLocalized("MMM")
            else -> "${w.first.dayOfMonth}/${w.first.monthValue}"
        }
        StatsTrendPoint(label, metric.average(inWindow), isCurrent = i == windows.lastIndex)
    }
