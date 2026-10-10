package uk.scimone.diafit.stats.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.stats.domain.StatsDay
import uk.scimone.diafit.stats.domain.StatsMetric
import uk.scimone.diafit.stats.domain.StatsPeriod
import java.time.LocalDate
import kotlin.math.roundToInt

private val SLEEP_METRICS = setOf(StatsMetric.SLEEP, StatsMetric.SLEEP_SCHEDULE)

/** One metric over a week, month or year in full: selectable bars, the numbers behind them, and the recent trend. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDetailScreen(userId: Int, metric: StatsMetric, period: StatsPeriod, offset: Int, onBack: () -> Unit) {
    val viewModel: StatsDetailViewModel = koinViewModel(key = "stats-detail-${metric.name}-${period.name}-$offset") {
        parametersOf(userId, metric, period, offset)
    }
    val state by viewModel.state.collectAsState()
    // Sleep has two views of the same nights; either card opens both.
    val title = if (metric in SLEEP_METRICS) "Sleep" else metric.title

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            PagerRow(state.window.label(LocalDate.now()), state.canGoNewer, viewModel::older, viewModel::newer)
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val metrics = if (metric in SLEEP_METRICS) listOf(StatsMetric.SLEEP, StatsMetric.SLEEP_SCHEDULE) else listOf(metric)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                metrics.forEach { m -> MetricSection(m, state, showTitle = metrics.size > 1) }
                TrendSection(metric, state)
                Spacer(Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun MetricSection(metric: StatsMetric, state: StatsDetailState, showTitle: Boolean) {
    val bars = remember(metric, state.window, state.days) { buildBars(metric, state.window, state.days) }
    var selected by remember(state.window) { mutableStateOf<Int?>(null) }
    val accent = metric.accent()
    val hasData = bars.any { it.segments != null }

    StatsCardFrame(metric, if (showTitle) (if (metric == StatsMetric.SLEEP) "Sleep duration" else "Sleep regularity") else metric.title) {
        if (!hasData) {
            Text("No data in this period", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@StatsCardFrame
        }
        // The readout above the chart: the tapped bar, else the period's daily average.
        val sel = selected?.let { i -> bars.getOrNull(i)?.segments?.let { state.window.buckets[i] to it } }
        if (sel != null) {
            Column {
                Text(sel.first.longLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BigValue(metric.formatBar(sel.second), "", accent)
            }
        } else {
            Headline(metric, state.days, state.period)
        }
        if (metric == StatsMetric.SLEEP_SCHEDULE) {
            SleepScheduleChart(bars, metric.colors().first(), selected = selected, onSelect = { selected = it })
        } else {
            StatsBarChart(
                bars, metric.colors(), height = 220.dp, showAxis = true, selected = selected, onSelect = { selected = it },
                fixedMax = if (metric == StatsMetric.GLUCOSE) 100.0 else null,
                formatAxis = { axisLabel(metric, it) }
            )
        }
        val segs = (sel?.second ?: metric.barSegments(state.days))
        if (metric.segmentLabels.size > 1 && metric != StatsMetric.SLEEP_SCHEDULE && segs != null) {
            Legend(metric, segs)
        }
        if (metric != StatsMetric.SLEEP_SCHEDULE) {
            HorizontalDivider()
            Summary(metric, state.days, state.period)
        }
    }
}

private fun axisLabel(metric: StatsMetric, v: Double): String = when (metric) {
    StatsMetric.GLUCOSE -> "${v.roundToInt()}%"
    StatsMetric.STEPS -> if (v >= 1000) "${(v / 1000).let { if (it % 1.0 == 0.0) it.roundToInt().toString() else "%.1f".format(it) }}k" else v.roundToInt().toString()
    StatsMetric.CALORIES -> if (v >= 1000) "${"%.1f".format(v / 1000)}k" else v.roundToInt().toString()
    StatsMetric.SLEEP -> "${if (v % 1.0 == 0.0) v.roundToInt().toString() else "%.1f".format(v)}h"
    StatsMetric.BOLUS, StatsMetric.BASAL -> if (v % 1.0 == 0.0) v.roundToInt().toString() else "%.1f".format(v)
    else -> v.roundToInt().toString()
}

@Composable
private fun Legend(metric: StatsMetric, segments: List<Double>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        metric.segmentLabels.forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(metric.colors()[i], CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(metric.formatSegment(i, segments[i]), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Summary(metric: StatsMetric, days: List<StatsDay>, period: StatsPeriod) {
    val withData = days.filter { metric.primary(it) != null }
    val high = withData.maxByOrNull { metric.primary(it)!! }
    val low = withData.minByOrNull { metric.primary(it)!! }
    val dateFmt = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryRow("Days with data", "${withData.size}")
        if (metric.additive) metric.total(days)?.let { total ->
            SummaryRow("Total", metric.format(total).let { "${it.value} ${it.unit}".trim() })
        }
        high?.let { SummaryRow("Highest day", "${metric.format(metric.primary(it)!!).let { f -> "${f.value} ${f.unit}".trim() }} · ${dateFmt.format(it.date)}") }
        low?.let { SummaryRow("Lowest day", "${metric.format(metric.primary(it)!!).let { f -> "${f.value} ${f.unit}".trim() }} · ${dateFmt.format(it.date)}") }
        if (metric == StatsMetric.GLUCOSE) {
            val g = days.mapNotNull { it.glucose }
            val n = g.sumOf { it.readingCount }.toDouble()
            if (n > 0) {
                val mean = g.sumOf { it.meanMgdl * it.readingCount } / n
                val sd = g.sumOf { it.sdMgdl * it.readingCount } / n
                SummaryRow("Mean glucose", "${mean.roundToInt()} mg/dL")
                SummaryRow("Standard deviation", "${sd.roundToInt()} mg/dL")
                SummaryRow("CV", "${(sd / mean * 100).roundToInt()}%")
                SummaryRow("GMI (estimated A1c)", "%.1f%%".format(uk.scimone.diafit.stats.domain.GlucoseDay.gmiOf(mean)))
            }
        }
        if (metric == StatsMetric.MEALS) {
            val m = days.filter { it.carbs != null }
            if (m.isNotEmpty()) {
                SummaryRow("Protein / day", "${m.map { it.protein ?: 0.0 }.average().roundToInt()} g")
                SummaryRow("Fat / day", "${m.map { it.fat ?: 0.0 }.average().roundToInt()} g")
                SummaryRow("Energy / day", "${m.map { it.mealKcal ?: 0.0 }.average().roundToInt()} kcal")
            }
        }
        if (period == StatsPeriod.YEAR) {
            Text("Bars show the daily average of each month.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrendSection(metric: StatsMetric, state: StatsDetailState) {
    val unitWord = when (state.period) { StatsPeriod.WEEK -> "weeks"; StatsPeriod.MONTH -> "months"; else -> "years" }
    // The sleep schedule has no single number to trend, so sleep trends its duration.
    val trendMetric = if (metric == StatsMetric.SLEEP_SCHEDULE) StatsMetric.SLEEP else metric
    StatsCardFrame(trendMetric, "Trend") {
        if (state.trend.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            return@StatsCardFrame
        }
        val points = state.trend
        val values = points.mapNotNull { it.value }
        if (values.size < 2) {
            Text("Not enough data yet to show a trend.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@StatsCardFrame
        }
        val current = points.last().value
        val previous = points.getOrNull(points.lastIndex - 1)?.value
        val change = changePercent(current, previous)
        Text(
            "Daily average over the last ${points.size} $unitWord",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (change != null) {
            val word = when (state.period) { StatsPeriod.WEEK -> "last week"; StatsPeriod.MONTH -> "last month"; else -> "last year" }
            val sign = if (change > 0) "+" else ""
            Text("$sign$change% vs $word", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = trendMetric.accent())
        }
        StatsTrendChart(points, trendMetric.accent())
    }
}
