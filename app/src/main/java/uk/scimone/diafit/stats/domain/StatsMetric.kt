package uk.scimone.diafit.stats.domain

/**
 * What a Stats card shows. A metric reduces a [StatsDay] to a few stacked [segments] (one for most; sleep
 * stages, macros or glucose zones for the stacked ones) whose sum is the bar, and to one [primary] number.
 */
enum class StatsMetric(
    val title: String,
    val unit: String,
    /** Whole days add up (steps, insulin...): the detail page also shows a total. Others (heart rate, sleep...) only average. */
    val additive: Boolean,
    val segmentLabels: List<String>
) {
    STEPS("Steps", "steps", true, listOf("Steps")),
    HEART_RATE("Heart rate", "bpm", false, listOf("Average")),
    RESTING_HEART_RATE("Resting heart rate", "bpm", false, listOf("Resting")),
    HEART_RATE_VARIABILITY("Heart rate variability", "ms", false, listOf("HRV (RMSSD)")),
    SLEEP("Sleep", "h", false, listOf("Deep", "REM", "Light")),
    SLEEP_SCHEDULE("Sleep schedule", "", false, listOf("Bedtime", "Wake-up")),
    ACTIVITY("Active time", "min", true, listOf("Active")),
    CALORIES("Calories burned", "kcal", true, listOf("Active", "Resting")),
    MEALS("Meals", "g carbs", true, listOf("Carbs", "Protein", "Fat")),
    GLUCOSE("Glucose", "% in range", false, listOf("Below", "In range", "Above")),
    BOLUS("Bolus insulin", "U", true, listOf("Bolus")),
    BASAL("Basal insulin", "U", true, listOf("Basal"));

    /** The segments of one day's bar (same order as [segmentLabels]), or null when the day has no data for this metric. */
    fun segments(day: StatsDay): List<Double>? = when (this) {
        STEPS -> day.steps?.let { listOf(it.toDouble()) }
        HEART_RATE -> day.avgBpm?.let { listOf(it) }
        RESTING_HEART_RATE -> day.restingBpm?.let { listOf(it) }
        HEART_RATE_VARIABILITY -> day.hrvMs?.let { listOf(it) }
        SLEEP -> day.sleep?.takeIf { it.asleepMs > 0 }?.let { listOf(it.deepMs.hours, it.remMs.hours, it.lightMs.hours) }
        SLEEP_SCHEDULE -> day.sleep?.takeIf { it.asleepMs > 0 }?.let { listOf(it.bedMinutes.toDouble(), it.wakeMinutes.toDouble()) }
        ACTIVITY -> day.activeMinutes?.let { listOf(it) }
        CALORIES -> day.totalKcal?.let { total ->
            val active = (day.activeKcal ?: 0.0).coerceIn(0.0, total)
            listOf(active, total - active)
        }
        MEALS -> day.carbs?.let { listOf(it, day.protein ?: 0.0, day.fat ?: 0.0) }
        GLUCOSE -> day.glucose?.let { listOf(it.belowShare * 100, it.inRangeShare * 100, it.aboveShare * 100) }
        BOLUS -> day.bolusUnits?.let { listOf(it) }
        BASAL -> day.basalUnits?.let { listOf(it) }
    }

    /** The one number that stands for the day (what the card headline averages). */
    fun primary(day: StatsDay): Double? {
        val s = segments(day) ?: return null
        return when (this) {
            MEALS -> s[0]
            GLUCOSE -> s[1]
            SLEEP_SCHEDULE -> s[0]
            else -> s.sum()
        }
    }

    /** Mean of every segment over the days of [days] that have data; null when none do. Used for a bar that spans several days. */
    fun barSegments(days: List<StatsDay>): List<Double>? {
        val all = days.mapNotNull(::segments)
        if (all.isEmpty()) return null
        return List(segmentLabels.size) { i -> all.sumOf { it[i] } / all.size }
    }

    /** Mean of [primary] over the days with data. */
    fun average(days: List<StatsDay>): Double? = days.mapNotNull(::primary).takeIf { it.isNotEmpty() }?.average()

    fun total(days: List<StatsDay>): Double? = days.mapNotNull(::primary).takeIf { it.isNotEmpty() }?.sum()

    fun daysWithData(days: List<StatsDay>): Int = days.count { segments(it) != null }

    private val Long.hours: Double get() = this / 3_600_000.0
}

/** The metrics shown as cards, in order, for each kind of page. */
object StatsCards {
    /** Day page: sleep schedule and per-signal heart-rate cards are folded into their parent card. */
    val day = listOf(
        StatsMetric.STEPS, StatsMetric.HEART_RATE, StatsMetric.SLEEP, StatsMetric.ACTIVITY, StatsMetric.CALORIES,
        StatsMetric.MEALS, StatsMetric.GLUCOSE, StatsMetric.BOLUS, StatsMetric.BASAL
    )

    /** Week / month / year pages: one card per metric. */
    val bars = listOf(
        StatsMetric.STEPS, StatsMetric.HEART_RATE, StatsMetric.RESTING_HEART_RATE, StatsMetric.HEART_RATE_VARIABILITY,
        StatsMetric.SLEEP, StatsMetric.SLEEP_SCHEDULE, StatsMetric.ACTIVITY, StatsMetric.CALORIES,
        StatsMetric.MEALS, StatsMetric.GLUCOSE, StatsMetric.BOLUS, StatsMetric.BASAL
    )
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
