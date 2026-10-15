package uk.scimone.diafit.stats.presentation

import uk.scimone.diafit.core.presentation.AppStrings
import uk.scimone.diafit.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Percent
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
import uk.scimone.diafit.ui.theme.FatColor
import uk.scimone.diafit.ui.theme.GlucoseNeutralColor
import uk.scimone.diafit.ui.theme.ProteinColor
import uk.scimone.diafit.ui.theme.InRange
import uk.scimone.diafit.ui.theme.SleepDeep
import uk.scimone.diafit.ui.theme.SleepLight
import uk.scimone.diafit.ui.theme.SleepRem
import java.util.Locale
import kotlin.math.roundToInt

/** Glucose measures that aren't a zone (mean, spread, A1c): a cool tone apart from the in/below/above colours. */

/** The colour of a metric's bars and figure. Heart rate, calories, steps and active time all share the activity colour. */
fun StatsMetric.accent(): Color = when (this) {
    StatsMetric.GLUCOSE_IN_RANGE -> InRange
    StatsMetric.GLUCOSE_BELOW -> BelowRange
    StatsMetric.GLUCOSE_ABOVE -> AboveRange
    StatsMetric.GLUCOSE_CV, StatsMetric.GLUCOSE_A1C, StatsMetric.GLUCOSE_COVERAGE -> GlucoseNeutralColor
    StatsMetric.BOLUS -> Bolus
    StatsMetric.BASAL -> Basal
    StatsMetric.CARBS -> Carbs
    StatsMetric.PROTEIN -> ProteinColor
    StatsMetric.FAT -> FatColor
    StatsMetric.MEAL_CALORIES -> Carbs
    StatsMetric.CALORIES, StatsMetric.ACTIVE_CALORIES, StatsMetric.STEPS, StatsMetric.ACTIVITY,
    StatsMetric.HEART_RATE, StatsMetric.RESTING_HEART_RATE, StatsMetric.HEART_RATE_VARIABILITY -> Activity
    StatsMetric.SLEEP, StatsMetric.SLEEP_SCHEDULE -> SleepLight
    StatsMetric.DEEP_SLEEP -> SleepDeep
    StatsMetric.REM_SLEEP -> SleepRem
    StatsMetric.LIGHT_SLEEP -> SleepLight
}

fun StatsMetric.colors(): List<Color> = when (this) {
    // Stacks: the very low / very high part in the full colour, the milder part lighter.
    StatsMetric.GLUCOSE_BELOW -> listOf(androidx.compose.ui.graphics.lerp(BelowRange, Color.Black, 0.45f), BelowRange)
    StatsMetric.GLUCOSE_ABOVE -> listOf(androidx.compose.ui.graphics.lerp(AboveRange, Color.Black, 0.45f), AboveRange)
    else -> listOf(accent())
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

private fun grouped(v: Double) = String.format(Locale.getDefault(), "%,d", v.roundToInt())

fun StatsMetric.format(value: Double): Formatted = when (this) {
    StatsMetric.STEPS -> Formatted(grouped(value), AppStrings.get(R.string.stats_unit_steps))
    StatsMetric.HEART_RATE, StatsMetric.RESTING_HEART_RATE -> Formatted(value.roundToInt().toString(), "bpm")
    StatsMetric.HEART_RATE_VARIABILITY -> Formatted(value.roundToInt().toString(), "ms")
    StatsMetric.SLEEP, StatsMetric.DEEP_SLEEP, StatsMetric.REM_SLEEP, StatsMetric.LIGHT_SLEEP -> Formatted(formatDurationHM(value), "")
    StatsMetric.SLEEP_SCHEDULE -> Formatted(formatClock(SleepNight.axisToClock(value.roundToInt())), "")
    StatsMetric.ACTIVITY -> if (value >= 60) Formatted(formatDurationHM(value / 60), "") else Formatted(value.roundToInt().toString(), "min")
    StatsMetric.CALORIES, StatsMetric.ACTIVE_CALORIES, StatsMetric.MEAL_CALORIES -> Formatted(grouped(value), "kcal")
    StatsMetric.CARBS, StatsMetric.PROTEIN, StatsMetric.FAT -> Formatted(value.roundToInt().toString(), "g")
    StatsMetric.GLUCOSE_IN_RANGE, StatsMetric.GLUCOSE_BELOW, StatsMetric.GLUCOSE_ABOVE, StatsMetric.GLUCOSE_CV, StatsMetric.GLUCOSE_COVERAGE ->
        Formatted(value.roundToInt().toString(), "%")
    StatsMetric.GLUCOSE_A1C -> Formatted(String.format(Locale.getDefault(), "%.1f", value), "%")
    StatsMetric.BOLUS, StatsMetric.BASAL -> Formatted(String.format(Locale.getDefault(), "%.1f", value), "U")
}

/** The figure joined with its unit on one line, for readouts: "45 g", "7 h 18". */
fun StatsMetric.formatInline(value: Double): String = format(value).let { "${it.value} ${it.unit}".trim() }

/** What a selected bar reads (the schedule shows both times). */
fun StatsMetric.formatBar(segments: List<Double>): String =
    if (this == StatsMetric.SLEEP_SCHEDULE) {
        "${formatClock(SleepNight.axisToClock(segments[0].roundToInt()))} – ${formatClock(SleepNight.axisToClock(segments[1].roundToInt()))}"
    } else formatInline(if (stacked) segments.sum() else segments[0])

/** "−4%" / "+12%": the change from [previous] to [current], null when it can't be told. */
fun changePercent(current: Double?, previous: Double?): Int? =
    if (current == null || previous == null || previous == 0.0) null else ((current - previous) / previous * 100).roundToInt()

fun StatsMetric.icon(): ImageVector = when (this) {
    StatsMetric.GLUCOSE_IN_RANGE -> Icons.Filled.WaterDrop
    StatsMetric.GLUCOSE_BELOW -> Icons.Filled.ArrowDownward
    StatsMetric.GLUCOSE_ABOVE -> Icons.Filled.ArrowUpward
    StatsMetric.GLUCOSE_CV -> Icons.Filled.Percent
    StatsMetric.GLUCOSE_COVERAGE -> Icons.Filled.Sensors
    StatsMetric.GLUCOSE_A1C -> Icons.Filled.Science
    StatsMetric.BOLUS, StatsMetric.BASAL -> Icons.Filled.Vaccines
    StatsMetric.CARBS, StatsMetric.PROTEIN, StatsMetric.FAT, StatsMetric.MEAL_CALORIES -> Icons.Filled.Restaurant
    StatsMetric.CALORIES, StatsMetric.ACTIVE_CALORIES -> Icons.Filled.LocalFireDepartment
    StatsMetric.STEPS -> Icons.AutoMirrored.Filled.DirectionsWalk
    StatsMetric.ACTIVITY -> Icons.Filled.FitnessCenter
    StatsMetric.HEART_RATE, StatsMetric.RESTING_HEART_RATE -> Icons.Filled.Favorite
    StatsMetric.HEART_RATE_VARIABILITY -> Icons.Filled.MonitorHeart
    StatsMetric.SLEEP, StatsMetric.DEEP_SLEEP, StatsMetric.REM_SLEEP, StatsMetric.LIGHT_SLEEP -> Icons.Filled.Bedtime
    StatsMetric.SLEEP_SCHEDULE -> Icons.Filled.Schedule
}

/** Line under the title of a W/M/Y card. */
fun StatsMetric.subtitle(): String = when (this) {
    StatsMetric.GLUCOSE_IN_RANGE -> AppStrings.get(R.string.stats_sub_tir)
    StatsMetric.GLUCOSE_BELOW -> AppStrings.get(R.string.stats_sub_below)
    StatsMetric.GLUCOSE_ABOVE -> AppStrings.get(R.string.stats_sub_above)
    StatsMetric.GLUCOSE_COVERAGE -> AppStrings.get(R.string.stats_sub_coverage)
    StatsMetric.SLEEP_SCHEDULE -> AppStrings.get(R.string.stats_sub_schedule)
    StatsMetric.SLEEP, StatsMetric.DEEP_SLEEP, StatsMetric.REM_SLEEP, StatsMetric.LIGHT_SLEEP -> AppStrings.get(R.string.stats_sub_per_night)
    else -> AppStrings.get(R.string.stats_sub_daily)
}

/** The y axis labels of a metric's chart. */
fun StatsMetric.axisLabel(v: Double): String = when (this) {
    StatsMetric.GLUCOSE_IN_RANGE, StatsMetric.GLUCOSE_BELOW, StatsMetric.GLUCOSE_ABOVE, StatsMetric.GLUCOSE_CV, StatsMetric.GLUCOSE_COVERAGE -> "${v.roundToInt()}%"
    StatsMetric.STEPS, StatsMetric.CALORIES, StatsMetric.ACTIVE_CALORIES, StatsMetric.MEAL_CALORIES ->
        if (v >= 1000) "${(v / 1000).let { if (it % 1.0 == 0.0) it.roundToInt().toString() else "%.1f".format(it) }}k" else v.roundToInt().toString()
    StatsMetric.SLEEP, StatsMetric.DEEP_SLEEP, StatsMetric.REM_SLEEP, StatsMetric.LIGHT_SLEEP ->
        "${if (v % 1.0 == 0.0) v.roundToInt().toString() else "%.1f".format(v)}h"
    StatsMetric.BOLUS, StatsMetric.BASAL, StatsMetric.GLUCOSE_A1C -> if (v % 1.0 == 0.0) v.roundToInt().toString() else "%.1f".format(v)
    else -> v.roundToInt().toString()
}
