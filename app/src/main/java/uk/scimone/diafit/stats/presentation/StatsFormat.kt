package uk.scimone.diafit.stats.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import uk.scimone.diafit.stats.domain.SleepNight
import uk.scimone.diafit.stats.domain.StatsMetric
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.Activity
import uk.scimone.diafit.ui.theme.Basal
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.InRange
import uk.scimone.diafit.ui.theme.SleepDeep
import uk.scimone.diafit.ui.theme.SleepLight
import uk.scimone.diafit.ui.theme.SleepRem
import java.util.Locale
import kotlin.math.roundToInt

private val HeartColor = Color(0xFFE5575F)
private val HrvColor = Color(0xFF26A69A)
private val BurnActive = Color(0xFFFF7043)
private val BurnResting = Color(0xFFFFAB91)
private val Protein = Color(0xFFEF9A9A)
private val Fat = Color(0xFFFFB74D)

/** One colour per segment of a metric's stacked bar, bottom segment first. */
fun StatsMetric.colors(): List<Color> = when (this) {
    StatsMetric.STEPS -> listOf(Activity)
    StatsMetric.HEART_RATE, StatsMetric.RESTING_HEART_RATE -> listOf(HeartColor)
    StatsMetric.HEART_RATE_VARIABILITY -> listOf(HrvColor)
    StatsMetric.SLEEP -> listOf(SleepDeep, SleepRem, SleepLight)
    StatsMetric.SLEEP_SCHEDULE -> listOf(SleepLight, SleepLight)
    StatsMetric.ACTIVITY -> listOf(Activity)
    StatsMetric.CALORIES -> listOf(BurnActive, BurnResting)
    StatsMetric.MEALS -> listOf(Carbs, Protein, Fat)
    StatsMetric.GLUCOSE -> listOf(BelowRange, InRange, AboveRange)
    StatsMetric.BOLUS -> listOf(Bolus)
    StatsMetric.BASAL -> listOf(Basal)
}

/** The colour that stands for the metric as a whole (card accent, headline value). */
fun StatsMetric.accent(): Color = when (this) {
    StatsMetric.GLUCOSE -> InRange
    StatsMetric.MEALS -> Carbs
    StatsMetric.CALORIES -> BurnActive
    StatsMetric.SLEEP -> SleepLight
    else -> colors().first()
}

/** A formatted value and its unit, kept apart so the number can be drawn large and the unit small. */
data class Formatted(val value: String, val unit: String)

fun formatDurationHM(hours: Double): String {
    val minutes = (hours * 60).roundToInt()
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"
}

fun formatClock(clockMinutes: Int): String {
    val m = ((clockMinutes % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

fun StatsMetric.format(value: Double): Formatted = when (this) {
    StatsMetric.STEPS -> Formatted(String.format(Locale.getDefault(), "%,d", value.roundToInt()), "steps")
    StatsMetric.HEART_RATE, StatsMetric.RESTING_HEART_RATE -> Formatted(value.roundToInt().toString(), "bpm")
    StatsMetric.HEART_RATE_VARIABILITY -> Formatted(value.roundToInt().toString(), "ms")
    StatsMetric.SLEEP -> formatDurationHM(value).let { Formatted(it, "") }
    StatsMetric.SLEEP_SCHEDULE -> Formatted(formatClock(SleepNight.axisToClock(value.roundToInt())), "")
    StatsMetric.ACTIVITY -> if (value >= 60) formatDurationHM(value / 60).let { Formatted(it, "") } else Formatted(value.roundToInt().toString(), "min")
    StatsMetric.CALORIES -> Formatted(String.format(Locale.getDefault(), "%,d", value.roundToInt()), "kcal")
    StatsMetric.MEALS -> Formatted(value.roundToInt().toString(), "g carbs")
    StatsMetric.GLUCOSE -> Formatted(value.roundToInt().toString(), "% in range")
    StatsMetric.BOLUS, StatsMetric.BASAL -> Formatted(String.format(Locale.getDefault(), "%.1f", value), "U")
}

/** The readout for one segment of a bar (a stage, a macro, a zone): "Deep 1 h 20", "Carbs 45 g". */
fun StatsMetric.formatSegment(index: Int, value: Double): String = when (this) {
    StatsMetric.SLEEP -> formatDurationHM(value)
    StatsMetric.GLUCOSE -> "${value.roundToInt()}%"
    StatsMetric.MEALS -> "${value.roundToInt()} g"
    StatsMetric.CALORIES -> "${value.roundToInt()} kcal"
    else -> format(value).let { "${it.value} ${it.unit}".trim() }
}

/** What a stacked bar adds up to, for the readout above a selected bar. */
fun StatsMetric.formatBar(segments: List<Double>): String = when (this) {
    StatsMetric.SLEEP -> formatDurationHM(segments.sum())
    StatsMetric.MEALS -> "${segments[0].roundToInt()} g carbs"
    StatsMetric.GLUCOSE -> "${segments[1].roundToInt()}% in range"
    StatsMetric.SLEEP_SCHEDULE -> "${formatClock(SleepNight.axisToClock(segments[0].roundToInt()))} – ${formatClock(SleepNight.axisToClock(segments[1].roundToInt()))}"
    else -> format(segments.sum()).let { "${it.value} ${it.unit}".trim() }
}

/** "−4%" / "+12%": the change from [previous] to [current], null when it can't be told. */
fun changePercent(current: Double?, previous: Double?): Int? =
    if (current == null || previous == null || previous == 0.0) null else ((current - previous) / previous * 100).roundToInt()

fun StatsMetric.icon(): ImageVector = when (this) {
    StatsMetric.STEPS -> Icons.AutoMirrored.Filled.DirectionsWalk
    StatsMetric.HEART_RATE -> Icons.Filled.Favorite
    StatsMetric.RESTING_HEART_RATE -> Icons.Filled.Favorite
    StatsMetric.HEART_RATE_VARIABILITY -> Icons.Filled.MonitorHeart
    StatsMetric.SLEEP -> Icons.Filled.Bedtime
    StatsMetric.SLEEP_SCHEDULE -> Icons.Filled.Schedule
    StatsMetric.ACTIVITY -> Icons.Filled.FitnessCenter
    StatsMetric.CALORIES -> Icons.Filled.LocalFireDepartment
    StatsMetric.MEALS -> Icons.Filled.Restaurant
    StatsMetric.GLUCOSE -> Icons.Filled.WaterDrop
    StatsMetric.BOLUS, StatsMetric.BASAL -> Icons.Filled.Vaccines
}

/** The title on a compact row, short enough to sit beside a chart and a figure. */
fun StatsMetric.shortTitle(): String = when (this) {
    StatsMetric.HEART_RATE_VARIABILITY -> "HRV"
    StatsMetric.RESTING_HEART_RATE -> "Resting HR"
    StatsMetric.HEART_RATE -> "Heart rate"
    StatsMetric.SLEEP_SCHEDULE -> "Sleep schedule"
    StatsMetric.ACTIVITY -> "Active time"
    StatsMetric.CALORIES -> "Calories"
    StatsMetric.BOLUS -> "Bolus"
    StatsMetric.BASAL -> "Basal"
    else -> title
}

/** What the figure on a compact W/M/Y row is. */
fun StatsMetric.subtitle(): String = when (this) {
    StatsMetric.GLUCOSE -> "Time in range"
    StatsMetric.SLEEP_SCHEDULE -> "Bed → wake, average"
    StatsMetric.CALORIES -> "Burned per day"
    StatsMetric.SLEEP -> "Asleep per night"
    StatsMetric.MEALS -> "Carbs per day"
    StatsMetric.HEART_RATE_VARIABILITY -> "Daily average"
    else -> "Daily average"
}
