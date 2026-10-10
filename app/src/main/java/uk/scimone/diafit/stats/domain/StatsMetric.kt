package uk.scimone.diafit.stats.domain

/**
 * What a W/M/Y card shows: one number per day. Everything is single-valued (a stacked quantity such as glucose zones
 * or macros is several metrics, each with its own card) except [SLEEP_SCHEDULE], whose two values are bedtime and
 * wake-up. [segments] is the value list of one day's bar, null when the day has no data.
 */
enum class StatsMetric(
    val title: String,
    /** Whole days add up (steps, insulin...): the detail page also shows a total. Others only average. */
    val additive: Boolean = false,
    /** Shown only on these pages (A1c is meaningless over a week); null = every W/M/Y page. */
    val periods: Set<StatsPeriod>? = null
) {
    GLUCOSE_IN_RANGE("In range"),
    GLUCOSE_BELOW("Below range"),
    GLUCOSE_ABOVE("Above range"),
    GLUCOSE_CV("Glucose variability (CV)"),
    GLUCOSE_A1C("A1c (GMI)", periods = setOf(StatsPeriod.MONTH, StatsPeriod.YEAR)),
    CARBS("Carbs", additive = true),
    PROTEIN("Protein", additive = true),
    FAT("Fat", additive = true),
    BOLUS("Bolus insulin", additive = true),
    BASAL("Basal insulin", additive = true),
    CALORIES("Calories burned", additive = true),
    ACTIVE_CALORIES("Active calories", additive = true),
    STEPS("Steps", additive = true),
    ACTIVITY("Active time", additive = true),
    HEART_RATE("Heart rate"),
    RESTING_HEART_RATE("Resting heart rate"),
    HEART_RATE_VARIABILITY("Heart rate variability"),
    SLEEP("Sleep"),
    DEEP_SLEEP("Deep sleep"),
    REM_SLEEP("REM sleep"),
    LIGHT_SLEEP("Light sleep"),
    SLEEP_SCHEDULE("Sleep schedule");

    /** The values of one day's bar, or null when the day has no data for this metric. */
    fun segments(day: StatsDay): List<Double>? {
        val v: Double? = when (this) {
            GLUCOSE_IN_RANGE -> day.glucose?.let { it.inRangeShare * 100 }
            GLUCOSE_BELOW -> day.glucose?.let { it.belowShare * 100 }
            GLUCOSE_ABOVE -> day.glucose?.let { it.aboveShare * 100 }
            GLUCOSE_CV -> day.glucose?.cvPercent
            GLUCOSE_A1C -> day.glucose?.gmi
            BOLUS -> day.bolusUnits
            BASAL -> day.basalUnits
            CARBS -> day.carbs
            PROTEIN -> day.protein?.takeIf { it > 0 }
            FAT -> day.fat?.takeIf { it > 0 }
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
    fun primary(day: StatsDay): Double? = segments(day)?.first()

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

/** The cards of each page, in order: glucose first, then meals, insulin, calories burned, then the rest. */
object StatsCards {
    /** Day page: one card per group (heart rate and sleep fold their parts in). */
    enum class DayCard { GLUCOSE, MEALS, BOLUS, BASAL, CALORIES, STEPS, ACTIVITY, HEART_RATE, SLEEP }

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
            StatsPeriod.MONTH -> w.first.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            else -> "${w.first.dayOfMonth}/${w.first.monthValue}"
        }
        StatsTrendPoint(label, metric.average(inWindow), isCurrent = i == windows.lastIndex)
    }
