package uk.scimone.diafit.history.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.DayGlucoseStats
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.core.domain.model.GlucoseZone
import uk.scimone.diafit.core.domain.model.zoneOf
import uk.scimone.diafit.history.presentation.detail.TimeInRangeBar
import uk.scimone.diafit.history.presentation.detail.ZONE_ORDER
import uk.scimone.diafit.history.presentation.detail.Metric
import uk.scimone.diafit.history.presentation.detail.percent
import uk.scimone.diafit.history.presentation.detail.zoneFill
import uk.scimone.diafit.history.presentation.model.DayHistoryUi
import uk.scimone.diafit.journal.presentation.components.formatUnits
import uk.scimone.diafit.ui.theme.Activity
import uk.scimone.diafit.ui.theme.Sleep
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.InRange
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val DateWidth = 44.dp
private val PercentWidth = 32.dp
private val MeanWidth = 30.dp
private val MiniBarWidth = 40.dp

/**
 * The period's statistics pinned on top, and below one compact row per day: time-in-range bar, mean
 * glucose and the day's carbs, insulin, steps and sleep as small bars scaled to the period's maxima.
 */
@Composable
internal fun HistoryStatsView(state: HistoryState, today: Long, onOpenDay: (Long) -> Unit) {
    val maxCarbs = remember(state.days) { state.days.maxOfOrNull { it.totalCarbs }?.coerceAtLeast(1f) ?: 1f }
    val maxInsulin = remember(state.days) { state.days.maxOfOrNull { it.totalInsulin }?.coerceAtLeast(1f) ?: 1f }
    val maxSteps = remember(state.days) { state.days.maxOfOrNull { it.steps }?.coerceAtLeast(1) ?: 1 }
    val maxSleep = remember(state.days) { state.days.maxOfOrNull { it.sleepMs }?.coerceAtLeast(1L) ?: 1L }

    Column(Modifier.fillMaxSize()) {
        PeriodSummaryCard(state, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        StatsHeaderRow(Modifier.padding(horizontal = 12.dp))
        HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            items(state.days, key = { it.epochDay }) { day ->
                DayStatsRow(day, state.thresholds, day.epochDay == today, maxCarbs, maxInsulin, maxSteps, maxSleep) { onOpenDay(day.epochDay) }
            }
        }
    }
}

@Composable
private fun PeriodSummaryCard(state: HistoryState, modifier: Modifier = Modifier) {
    val stats = state.periodStats
    val dayCount = state.days.count { it.stats != null || it.totalCarbs > 0 || it.totalInsulin > 0 }.coerceAtLeast(1)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (stats == null) {
                Text("No glucose readings in this period", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${percent(stats.inRangeShare)}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                        color = if (stats.inRangeShare >= 0.7) InRange else MaterialTheme.colorScheme.onSurface)
                    Text("%", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 5.dp, start = 2.dp))
                    Text("  time in range", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp).weight(1f))
                    Text(
                        "${state.thresholds.low}–${state.thresholds.high} mg/dL",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                TimeInRangeBar(stats)
                ZoneShareLine(stats)
            }
            Row(Modifier.fillMaxWidth()) {
                if (stats != null) {
                    Metric("Average", "${stats.meanMgdl.toInt()}", "mg/dL", Modifier.weight(1f))
                    Metric("Variability", "${stats.cvPercent.toInt()}", "% CV", Modifier.weight(1f), valueColor = if (stats.cvPercent > 36) AboveRange else null)
                }
                Metric("Carbs / day", "${Math.round(state.days.sumOf { it.totalCarbs.toDouble() } / dayCount)}", "g", Modifier.weight(1f), valueColor = Carbs)
                Metric("Insulin / day", formatUnits(state.days.sumOf { it.totalInsulin.toDouble() } / dayCount), "U", Modifier.weight(1f), valueColor = Bolus)
            }
            // Activity averages, over the days that have any of the data (a missing wearable day isn't a zero).
            val stepDays = state.days.filter { it.steps > 0 }
            val sleepNights = state.days.filter { it.sleepMs > 0 }
            val activeDays = state.days.filter { it.activeMs > 0 }
            if (stepDays.isNotEmpty() || sleepNights.isNotEmpty() || activeDays.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth()) {
                    Metric("Steps / day", if (stepDays.isEmpty()) "–" else "%,d".format(stepDays.sumOf { it.steps } / stepDays.size), "", Modifier.weight(1f), valueColor = Activity.takeIf { stepDays.isNotEmpty() })
                    Metric("Active / day", if (activeDays.isEmpty()) "–" else compactDuration(activeDays.sumOf { it.activeMs } / state.days.size.coerceAtLeast(1)), "", Modifier.weight(1f), valueColor = Activity.takeIf { activeDays.isNotEmpty() })
                    Metric("Sleep / night", if (sleepNights.isEmpty()) "–" else compactDuration(sleepNights.sumOf { it.sleepMs } / sleepNights.size), "", Modifier.weight(1f), valueColor = Sleep.takeIf { sleepNights.isNotEmpty() })
                }
            }
        }
    }
}

/** Compact legend under the period bar: the share of each zone that has any. */
@Composable
private fun ZoneShareLine(stats: DayGlucoseStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ZONE_ORDER.filter { stats.share(it) > 0.0 }.forEach { zone ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(zoneFill(zone), RoundedCornerShape(2.dp)))
                Text(
                    " ${zoneShortName(zone)} ${percent(stats.share(zone))}%",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun zoneShortName(zone: GlucoseZone) = when (zone) {
    GlucoseZone.VERY_LOW -> "V. low"
    GlucoseZone.LOW -> "Low"
    GlucoseZone.IN_RANGE -> "In range"
    GlucoseZone.HIGH -> "High"
    GlucoseZone.VERY_HIGH -> "V. high"
}

@Composable
private fun StatsHeaderRow(modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(DateWidth))
        Text("Time in range", style = style, color = color, modifier = Modifier.weight(1f).padding(start = PercentWidth))
        Text("Avg", style = style, color = color, modifier = Modifier.width(MeanWidth), textAlign = TextAlign.End)
        Spacer(Modifier.width(8.dp))
        Text("Carbs", style = style, color = Carbs, modifier = Modifier.width(MiniBarWidth))
        Spacer(Modifier.width(6.dp))
        Text("Insulin", style = style, color = Bolus, modifier = Modifier.width(MiniBarWidth))
        Spacer(Modifier.width(6.dp))
        Text("Steps", style = style, color = Activity, modifier = Modifier.width(MiniBarWidth))
        Spacer(Modifier.width(6.dp))
        Text("Sleep", style = style, color = Sleep, modifier = Modifier.width(MiniBarWidth))
    }
}

@Composable
private fun DayStatsRow(
    day: DayHistoryUi,
    thresholds: GlucoseThresholds,
    isToday: Boolean,
    maxCarbs: Float,
    maxInsulin: Float,
    maxSteps: Int,
    maxSleep: Long,
    onClick: () -> Unit
) {
    val stats = day.stats
    val date = LocalDate.ofEpochDay(day.epochDay)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).height(34.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${date.dayOfMonth}",
            Modifier.width(DateWidth),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (stats == null) "–" else "${percent(stats.inRangeShare)}%",
            Modifier.width(PercentWidth),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (stats != null && stats.inRangeShare >= 0.7) InRange else MaterialTheme.colorScheme.onSurface
        )
        Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
            if (stats != null) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    ZONE_ORDER.forEach { zone ->
                        val share = stats.share(zone)
                        if (share > 0.0) Box(Modifier.weight(share.toFloat()).fillMaxHeight().background(zoneFill(zone)))
                    }
                }
            }
        }
        Text(
            if (stats == null) "" else "${stats.meanMgdl.toInt()}",
            Modifier.width(MeanWidth),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            color = if (stats == null) MaterialTheme.colorScheme.onSurface else meanColor(thresholds, stats.meanMgdl.toInt())
        )
        Spacer(Modifier.width(8.dp))
        MiniBar(day.totalCarbs, maxCarbs, if (day.totalCarbs > 0) "${day.totalCarbs.toInt()} g" else "", Carbs)
        Spacer(Modifier.width(6.dp))
        MiniBar(day.totalInsulin, maxInsulin, if (day.totalInsulin > 0) "${formatUnits(day.totalInsulin.toDouble())} U" else "", Bolus)
        Spacer(Modifier.width(6.dp))
        MiniBar(day.steps.toFloat(), maxSteps.toFloat(), if (day.steps > 0) compactCount(day.steps) else "", Activity)
        Spacer(Modifier.width(6.dp))
        MiniBar(day.sleepMs.toFloat(), maxSleep.toFloat(), if (day.sleepMs > 0) compactDuration(day.sleepMs) else "", Sleep)
    }
}

@Composable
private fun meanColor(t: GlucoseThresholds, mean: Int): Color = when (t.zoneOf(mean)) {
    GlucoseZone.IN_RANGE -> MaterialTheme.colorScheme.onSurface
    GlucoseZone.LOW, GlucoseZone.VERY_LOW -> BelowRange
    GlucoseZone.HIGH, GlucoseZone.VERY_HIGH -> AboveRange
}

/** "8.2k" for 8 234 steps, "950" below 1 000. */
private fun compactCount(n: Int): String = if (n >= 1000) "%.1fk".format(Locale.US, n / 1000.0) else "$n"

/** "7h12" / "45m". */
private fun compactDuration(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes >= 60) "${minutes / 60}h%02d".format(minutes % 60) else "${minutes}m"
}

/** A horizontal bar filled to [value]/[max] with its label on top. */
@Composable
private fun MiniBar(value: Float, max: Float, label: String, color: Color, width: Dp = MiniBarWidth) {
    Box(
        Modifier.width(width).height(16.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth((value / max).coerceIn(0.04f, 1f)).background(color.copy(alpha = 0.55f)))
        Text(label, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
    }
}
