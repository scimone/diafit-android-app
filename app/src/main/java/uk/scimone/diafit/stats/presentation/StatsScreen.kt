package uk.scimone.diafit.stats.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Insights
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.core.data.healthconnect.HealthConnectPermissions
import uk.scimone.diafit.core.domain.model.SleepStage
import uk.scimone.diafit.stats.domain.SleepNight
import uk.scimone.diafit.stats.domain.StatsCards
import uk.scimone.diafit.stats.domain.StatsDay
import uk.scimone.diafit.stats.domain.StatsMetric
import uk.scimone.diafit.stats.domain.StatsPeriod
import uk.scimone.diafit.stats.domain.StatsWindow
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.InRange
import uk.scimone.diafit.ui.theme.SleepAwake
import uk.scimone.diafit.ui.theme.SleepDeep
import uk.scimone.diafit.ui.theme.SleepLight
import uk.scimone.diafit.ui.theme.SleepRem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** The Stats tab: pick a time frame (D W M Y), page through it, and read one card per metric. */
@Composable
fun StatsScreen(userId: Int, onOpenMetric: (StatsMetric, StatsPeriod, Int) -> Unit) {
    val viewModel: StatsViewModel = koinViewModel { parametersOf(userId) }
    val state by viewModel.state.collectAsState()

    val vitalsPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.onVitalsPermissionResult()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize()) {
        PeriodHeader(
            period = state.period,
            label = state.window.label(LocalDate.now()),
            canGoNewer = state.canGoNewer,
            onPeriod = viewModel::setPeriod,
            onOlder = viewModel::older,
            onNewer = viewModel::newer
        )
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.needsVitalsPermission) {
                    item(key = "permission") {
                        VitalsPermissionCard(onAllow = { vitalsPermission.launch(HealthConnectPermissions.vitals) })
                    }
                }
                state.errorMessage?.let { item(key = "error") { Text(it, color = MaterialTheme.colorScheme.error) } }
                if (state.period == StatsPeriod.DAY) {
                    val day = state.days.firstOrNull()
                    // Cards without data are left out, so what is shown is what there is.
                    val shown = StatsCards.day.filter { day != null && hasDayData(it, day) }
                    items(shown, key = { "day-${it.name}" }) { metric -> DayCard(metric, day!!) }
                    if (shown.isEmpty() && !state.isLoading) item(key = "empty") { EmptyState() }
                } else {
                    val shown = StatsCards.bars.filter { it.daysWithData(state.days) > 0 }
                    items(shown, key = { "bars-${it.name}" }) { metric ->
                        BarCard(metric, state.window, state.days, onClick = { onOpenMetric(metric, state.period, state.offset) })
                    }
                    if (shown.isEmpty() && !state.isLoading) item(key = "empty") { EmptyState() }
                }
            }
            if (state.isLoading && state.days.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun PeriodHeader(
    period: StatsPeriod,
    label: String,
    canGoNewer: Boolean,
    onPeriod: (StatsPeriod) -> Unit,
    onOlder: () -> Unit,
    onNewer: () -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsPeriod.entries.forEach { p ->
                FilterChip(
                    selected = p == period,
                    onClick = { onPeriod(p) },
                    label = { Text(p.label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        PagerRow(label, canGoNewer, onOlder, onNewer)
    }
}

/** ‹ label › : the previous and next period around the title. */
@Composable
internal fun PagerRow(label: String, canGoNewer: Boolean, onOlder: () -> Unit, onNewer: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOlder) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier") }
        Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onNewer, enabled = canGoNewer) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later") }
    }
}

@Composable
private fun VitalsPermissionCard(onAllow: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("More from Health Connect", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Allow resting heart rate, heart rate variability and calories burned to fill those cards.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onAllow) { Text("Allow access") }
        }
    }
}

// ---------------------------------------------------------------- card building blocks

private val CardShape = RoundedCornerShape(20.dp)

/** The app's card surface (as in the Journal): rounded, hairline border, no fill change. */
@Composable
internal fun StatsSurface(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val color = MaterialTheme.colorScheme.surface
    if (onClick != null) Surface(onClick = onClick, shape = CardShape, color = color, border = border, modifier = modifier.fillMaxWidth(), content = content)
    else Surface(shape = CardShape, color = color, border = border, modifier = modifier.fillMaxWidth(), content = content)
}

/** The tinted rounded tile with the metric's glyph that every Journal entry uses. */
@Composable
internal fun IconTile(icon: ImageVector, accent: Color, size: Dp = 44.dp) {
    Box(Modifier.size(size).background(accent.copy(alpha = 0.16f), RoundedCornerShape(size * 0.3f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(size * 0.5f))
    }
}

/** The figure on the right of a card: same type size everywhere, unit beneath it. */
@Composable
internal fun FigureBlock(value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1, softWrap = false)
        if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, softWrap = false)
    }
}

/** Icon tile + title/subtitle, with an optional figure on the right: the head of every card. */
@Composable
internal fun CardHeader(
    metric: StatsMetric,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconTile(metric.icon(), metric.accent())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        trailing?.invoke()
    }
}

/** A titled block of the detail pages. */
@Composable
internal fun StatsCardFrame(
    metric: StatsMetric,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    StatsSurface(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(metric, title, null)
            content()
        }
    }
}

@Composable
internal fun BigValue(value: String, unit: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
        if (unit.isNotEmpty()) {
            Text(" $unit", Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String, unit: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            if (unit.isNotEmpty()) Text(" $unit", Modifier.padding(bottom = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Insights, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text("Nothing recorded in this period", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Try an earlier period, or connect more data sources in Settings.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------- day cards

private fun hasDayData(metric: StatsMetric, day: StatsDay): Boolean =
    if (metric == StatsMetric.HEART_RATE) day.avgBpm != null || day.restingBpm != null || day.hrvMs != null
    else metric.segments(day) != null

@Composable
private fun DayCard(metric: StatsMetric, day: StatsDay) {
    val accent = metric.accent()
    val title = if (metric == StatsMetric.ACTIVITY) "Activity" else metric.title
    when (metric) {
        // One figure: a compact row.
        StatsMetric.STEPS, StatsMetric.ACTIVITY, StatsMetric.BOLUS, StatsMetric.BASAL, StatsMetric.CALORIES -> StatsSurface {
            val f = metric.format(metric.primary(day)!!)
            val sub = if (metric == StatsMetric.CALORIES && day.activeKcal != null) {
                "Active ${day.activeKcal.roundToInt()} · Resting ${(day.totalKcal!! - day.activeKcal).roundToInt()}"
            } else null
            CardHeader(metric, title, sub, Modifier.padding(12.dp)) { FigureBlock(f.value, f.unit, accent) }
        }
        StatsMetric.HEART_RATE -> StatsSurface {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardHeader(metric, title, null)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledValue("Resting", day.restingBpm?.roundToInt()?.toString() ?: "–", "bpm", accent, Modifier.weight(1f))
                    LabeledValue("Average", day.avgBpm?.roundToInt()?.toString() ?: "–", "bpm", accent, Modifier.weight(1f))
                    LabeledValue("Variability", day.hrvMs?.roundToInt()?.toString() ?: "–", "ms", StatsMetric.HEART_RATE_VARIABILITY.accent(), Modifier.weight(1f))
                }
            }
        }
        StatsMetric.SLEEP -> StatsSurface {
            val night = day.sleep!!
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardHeader(metric, title, sleepRange(night)) {
                    FigureBlock(formatDurationHM(night.asleepMs / 3_600_000.0), "asleep", SleepLight)
                }
                SleepStages(night)
            }
        }
        StatsMetric.MEALS -> StatsSurface {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardHeader(metric, title, null) { FigureBlock("${day.carbs!!.roundToInt()}", "g carbs", accent) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledValue("Protein", "${(day.protein ?: 0.0).roundToInt()}", "g", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    LabeledValue("Fat", "${(day.fat ?: 0.0).roundToInt()}", "g", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    LabeledValue("Energy", "${(day.mealKcal ?: 0.0).roundToInt()}", "kcal", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
            }
        }
        StatsMetric.GLUCOSE -> StatsSurface {
            val g = day.glucose!!
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardHeader(metric, title, null) { FigureBlock("${(g.inRangeShare * 100).roundToInt()}", "% in range", InRange) }
                GlucoseDayContent(g)
            }
        }
        else -> Unit
    }
}

private val clock = DateTimeFormatter.ofPattern("HH:mm")

private fun sleepRange(night: SleepNight): String? {
    if (night.endUtc <= 0) return null
    val zone = ZoneId.systemDefault()
    fun at(utc: Long) = clock.format(Instant.ofEpochMilli(utc).atZone(zone))
    return "${at(night.startUtc)} – ${at(night.endUtc)}"
}

@Composable
private fun SleepStages(night: SleepNight) {
    if (night.hasStages && night.spans.isNotEmpty()) {
        // Stages in the order they happened, so the night's shape (deep early, REM late) is visible.
        val total = night.spans.sumOf { it.endUtc - it.startUtc }.coerceAtLeast(1L)
        SegmentedBar(night.spans.map { ((it.endUtc - it.startUtc) / total.toFloat()) to sleepStageColor(it.stage) }, height = 14.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StageLegend("Deep", night.deepMs, SleepDeep)
            StageLegend("REM", night.remMs, SleepRem)
            StageLegend("Light", night.lightMs, SleepLight)
            StageLegend("Awake", night.awakeMs, SleepAwake)
        }
    } else {
        Text("No sleep stages were recorded for this night.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun sleepStageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.AWAKE -> SleepAwake
    SleepStage.REM -> SleepRem
    SleepStage.LIGHT -> SleepLight
    SleepStage.DEEP -> SleepDeep
    SleepStage.SLEEPING -> SleepLight
}

@Composable
private fun StageLegend(label: String, ms: Long, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatDurationHM(ms / 3_600_000.0), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GlucoseDayContent(g: uk.scimone.diafit.stats.domain.GlucoseDay) {
    SegmentedBar(
        listOf(
            g.belowShare.toFloat() to BelowRange,
            g.inRangeShare.toFloat() to InRange,
            g.aboveShare.toFloat() to AboveRange
        ),
        height = 14.dp
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ZoneLegend("Below", g.belowShare, BelowRange)
        ZoneLegend("In range", g.inRangeShare, InRange)
        ZoneLegend("Above", g.aboveShare, AboveRange)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledValue("Mean", g.meanMgdl.roundToInt().toString(), "mg/dL", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        LabeledValue("Std dev", g.sdMgdl.roundToInt().toString(), "mg/dL", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        LabeledValue("CV", "${g.cvPercent.roundToInt()}", "%", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        LabeledValue("GMI", String.format("%.1f", g.gmi), "% A1c", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
    }
}

@Composable
private fun ZoneLegend(label: String, share: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text("$label ${(share * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- week / month / year rows

/** The bars a W/M/Y chart draws: one per bucket, the mean of its days that have data. */
internal fun buildBars(metric: StatsMetric, window: StatsWindow, days: List<StatsDay>): List<BarData> =
    window.buckets.map { bucket -> BarData(bucket.label, metric.barSegments(days.filter { it.date in bucket })) }

/** One compact row per metric (icon, name, tiny chart, average) so many can be compared at a glance. */
@Composable
private fun BarCard(metric: StatsMetric, window: StatsWindow, days: List<StatsDay>, onClick: () -> Unit) {
    val bars = remember(metric, window, days) { buildBars(metric, window, days) }
    val figure = headlineFigure(metric, days)
    StatsSurface(onClick = onClick) {
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(metric.icon(), metric.accent())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(metric.shortTitle(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(metric.subtitle(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            if (metric == StatsMetric.SLEEP_SCHEDULE) {
                SleepScheduleChart(bars, metric.colors().first(), Modifier.width(76.dp), height = 40.dp, compact = true)
            } else {
                StatsBarChart(bars, metric.colors(), Modifier.width(76.dp), height = 40.dp, fixedMax = if (metric == StatsMetric.GLUCOSE) 100.0 else null)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) {
                FigureBlock(figure.value, figure.unit, metric.accent())
            }
        }
    }
}

/** The average a compact row shows (sleep schedule: the typical bedtime). */
internal fun headlineFigure(metric: StatsMetric, days: List<StatsDay>): Formatted {
    if (metric == StatsMetric.SLEEP_SCHEDULE) {
        val nights = days.mapNotNull { metric.segments(it) }
        return Formatted(metric.format(nights.map { it[0] }.average()).value, "→ " + metric.format(nights.map { it[1] }.average()).value)
    }
    return metric.format(metric.average(days) ?: 0.0)
}

/** "Daily average" with the number, in the card's accent colour (detail pages). */
@Composable
internal fun Headline(metric: StatsMetric, days: List<StatsDay>, period: StatsPeriod) {
    val accent = metric.accent()
    if (metric == StatsMetric.SLEEP_SCHEDULE) {
        val nights = days.mapNotNull { metric.segments(it) }
        val bed = nights.map { it[0] }.average(); val wake = nights.map { it[1] }.average()
        Column {
            Text("Average", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BigValue("${metric.format(bed).value} – ${metric.format(wake).value}", "", accent)
        }
        return
    }
    val avg = metric.average(days) ?: return
    Column {
        Text("Daily average", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        metric.format(avg).let { BigValue(it.value, it.unit, accent) }
    }
}
