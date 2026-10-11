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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
            current = state.window.first,
            onPeriod = viewModel::setPeriod,
            onOlder = viewModel::older,
            onNewer = viewModel::newer,
            onPick = viewModel::goTo
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
                    dayCardItems(state.days.firstOrNull(), state.isLoading)
                } else {
                    val shown = StatsCards.bars(state.period).filter { it.daysWithData(state.days) > 0 }
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
    current: LocalDate,
    onPeriod: (StatsPeriod) -> Unit,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onPick: (LocalDate) -> Unit
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
        PagerRow(label, canGoNewer, current, onOlder, onNewer, onPick)
    }
}

/** ‹ label › : the previous and next period around the title; tapping the title opens a date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PagerRow(label: String, canGoNewer: Boolean, current: LocalDate, onOlder: () -> Unit, onNewer: () -> Unit, onPick: (LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOlder) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier") }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { picking = true }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.CalendarMonth, "Pick a date", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onNewer, enabled = canGoNewer) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later") }
    }
    if (picking) {
        val zone = java.time.ZoneId.systemDefault()
        val todayMillis = LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            }
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
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
    if (unit.length in 1..2) {
        // A short unit (U, g, %, ms) sits right next to the number.
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1, softWrap = false)
            Text(" $unit", Modifier.padding(bottom = 2.dp), style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, softWrap = false)
        }
        return
    }
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

/** One day's cards (cards without data are left out, so what is shown is what there is); also used by History's day page. */
internal fun androidx.compose.foundation.lazy.LazyListScope.dayCardItems(day: StatsDay?, isLoading: Boolean) {
    val shown = StatsCards.day.filter { day != null && hasDayData(it, day) }
    items(shown, key = { "day-${it.name}" }) { card -> DayCard(card, day!!) }
    if (shown.isEmpty() && !isLoading) item(key = "empty") { EmptyState() }
}

private fun hasDayData(card: StatsCards.DayCard, day: StatsDay): Boolean = when (card) {
    StatsCards.DayCard.GLUCOSE -> day.glucose != null
    StatsCards.DayCard.INSULIN -> day.hasInsulin
    StatsCards.DayCard.MEALS -> day.carbs != null
    StatsCards.DayCard.ACTIVITY ->
        day.steps != null || day.activeMinutes != null || day.totalKcal != null || day.avgBpm != null || day.restingBpm != null || day.hrvMs != null
    StatsCards.DayCard.SLEEP -> day.sleep?.let { it.asleepMs > 0 } == true
}

@Composable
private fun DayCard(card: StatsCards.DayCard, day: StatsDay) {
    val ink = MaterialTheme.colorScheme.onSurface
    when (card) {
        StatsCards.DayCard.GLUCOSE -> StatsSurface {
            val g = day.glucose!!
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardHeader(StatsMetric.GLUCOSE_IN_RANGE, "Glucose", null) { FigureBlock("${(g.inRangeShare * 100).roundToInt()}", "% in range", InRange) }
                GlucoseDayContent(g)
            }
        }
        StatsCards.DayCard.INSULIN -> InsulinDayCard(day)
        StatsCards.DayCard.MEALS -> StatsSurface {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardHeader(StatsMetric.CARBS, "Meals", "Carbohydrates") { FigureBlock("${day.carbs!!.roundToInt()}", "g", StatsMetric.CARBS.accent()) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledValue("Protein", "${(day.protein ?: 0.0).roundToInt()}", "g", StatsMetric.PROTEIN.accent(), Modifier.weight(1f))
                    LabeledValue("Fat", "${(day.fat ?: 0.0).roundToInt()}", "g", StatsMetric.FAT.accent(), Modifier.weight(1f))
                    LabeledValue("Energy", "${(day.mealKcal ?: 0.0).roundToInt()}", "kcal", ink, Modifier.weight(1f))
                }
            }
        }
        StatsCards.DayCard.ACTIVITY -> ActivityDayCard(day)
        StatsCards.DayCard.SLEEP -> StatsSurface {
            val night = day.sleep!!
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CardHeader(StatsMetric.SLEEP, "Sleep", sleepRange(night)) {
                    FigureBlock(formatDurationHM(night.asleepMs / 3_600_000.0), "asleep", SleepLight)
                }
                SleepStages(night)
            }
        }
    }
}

/** Total daily insulin as the headline; the bar and legend show how much of it was basal vs bolus. */
@Composable
private fun InsulinDayCard(day: StatsDay) {
    val bolus = day.bolusUnits ?: 0.0
    val basal = day.basalUnits ?: 0.0
    val total = bolus + basal
    val bolusColor = StatsMetric.BOLUS.accent()
    val basalColor = StatsMetric.BASAL.accent()
    StatsSurface {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(StatsMetric.BOLUS, "Insulin", "Total daily dose") {
                val f = StatsMetric.BOLUS.format(total)
                FigureBlock(f.value, f.unit, bolusColor)
            }
            if (total > 0) {
                SegmentedBar(listOf((basal / total).toFloat() to basalColor, (bolus / total).toFloat() to bolusColor), height = 14.dp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Both are shown even when one is zero.
                StatsMetric.BASAL.format(basal).let {
                    LabeledValue("Basal · ${(basal / total * 100).roundToInt()}%", it.value, it.unit, basalColor, Modifier.weight(1f))
                }
                StatsMetric.BOLUS.format(bolus).let {
                    LabeledValue("Bolus · ${(bolus / total * 100).roundToInt()}%", it.value, it.unit, bolusColor, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Movement and body load in one card: steps lead, then active time and calories; heart rate sits under a divider. */
@Composable
private fun ActivityDayCard(day: StatsDay) {
    val accent = StatsMetric.ACTIVITY.accent()
    val hasHeart = day.restingBpm != null || day.avgBpm != null || day.hrvMs != null
    StatsSurface {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(StatsMetric.ACTIVITY, "Activity", day.activeKcal?.let { "Active ${it.roundToInt()} kcal · Resting ${((day.totalKcal ?: it) - it).roundToInt()} kcal" }) {
                day.steps?.let { FigureBlock(StatsMetric.STEPS.format(it.toDouble()).value, "steps", accent) }
            }
            if (day.activeMinutes != null || day.totalKcal != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    day.activeMinutes?.let { val f = StatsMetric.ACTIVITY.format(it); LabeledValue("Active time", f.value, f.unit, accent, Modifier.weight(1f)) }
                    day.totalKcal?.let { val f = StatsMetric.CALORIES.format(it); LabeledValue("Calories burned", f.value, f.unit, accent, Modifier.weight(1f)) }
                }
            }
            if (day.activeKcal != null && day.totalKcal != null && day.totalKcal > 0) {
                val active = day.activeKcal.coerceIn(0.0, day.totalKcal)
                val resting = day.totalKcal - active
                SegmentedBar(listOf((resting / day.totalKcal).toFloat() to accent.copy(alpha = 0.4f), (active / day.totalKcal).toFloat() to accent), height = 14.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    KcalLegend("Resting", resting, accent.copy(alpha = 0.4f))
                    KcalLegend("Active", active, accent)
                }
            }
            if (hasHeart) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledValue("Resting HR", day.restingBpm?.roundToInt()?.toString() ?: "–", "bpm", accent, Modifier.weight(1f))
                    LabeledValue("Average HR", day.avgBpm?.roundToInt()?.toString() ?: "–", "bpm", accent, Modifier.weight(1f))
                    LabeledValue("HRV", day.hrvMs?.roundToInt()?.toString() ?: "–", "ms", accent, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KcalLegend(label: String, kcal: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text("$label ${grouped(kcal)} kcal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun grouped(v: Double) = String.format(java.util.Locale.getDefault(), "%,d", v.roundToInt())

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
        // One lane per stage on the night's own time line, so the shape of the night (deep early, REM late) stays visible.
        val from = night.spans.minOf { it.startUtc }
        val total = (night.spans.maxOf { it.endUtc } - from).coerceAtLeast(1L).toFloat()
        val rows = listOf(
            Triple("Awake", SleepStage.AWAKE, night.awakeMs to SleepAwake),
            Triple("REM", SleepStage.REM, night.remMs to SleepRem),
            Triple("Light", SleepStage.LIGHT, night.lightMs to SleepLight),
            Triple("Deep", SleepStage.DEEP, night.deepMs to SleepDeep)
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { (label, stage, ms) ->
                val spans = night.spans.filter { it.stage == stage || (stage == SleepStage.LIGHT && it.stage == SleepStage.SLEEPING) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.width(44.dp), style = MaterialTheme.typography.labelMedium, color = ms.second)
                    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    androidx.compose.foundation.Canvas(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp))) {
                        drawRect(track)
                        spans.forEach {
                            val x0 = (it.startUtc - from) / total * size.width
                            val x1 = (it.endUtc - from) / total * size.width
                            drawRect(ms.second, androidx.compose.ui.geometry.Offset(x0, 0f), androidx.compose.ui.geometry.Size((x1 - x0).coerceAtLeast(1.5f), size.height))
                        }
                    }
                    Text(
                        formatDurationHM(ms.first / 3_600_000.0), Modifier.width(60.dp),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End
                    )
                }
            }
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledValue("Data coverage", "${(g.coverage * 100).roundToInt()}", "%", StatsMetric.GLUCOSE_COVERAGE.accent(), Modifier.weight(1f))
        LabeledValue("Readings", g.readingCount.toString(), "", MaterialTheme.colorScheme.onSurface, Modifier.weight(3f))
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

/** One card per metric: its average up top and a large bar chart (a bar per day, per month for a year) to compare the periods. */
@Composable
private fun BarCard(metric: StatsMetric, window: StatsWindow, days: List<StatsDay>, onClick: () -> Unit) {
    val bars = remember(metric, window, days) { buildBars(metric, window, days) }
    val figure = headlineFigure(metric, days)
    val accent = metric.accent()
    StatsSurface(onClick = onClick) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader(metric, metric.title, metric.subtitle()) {
                if (metric == StatsMetric.SLEEP_SCHEDULE) {
                    Text(figure.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent, maxLines = 1, softWrap = false)
                } else FigureBlock(figure.value, figure.unit, accent)
            }
            if (metric == StatsMetric.SLEEP_SCHEDULE) {
                SleepScheduleChart(bars, accent, height = 170.dp)
            } else {
                StatsBarChart(
                    bars, metric.colors(), height = if (bars.size <= 12) 150.dp else 140.dp, showAxis = true,
                    fixedMax = if (metric == StatsMetric.GLUCOSE_IN_RANGE) 100.0 else null,
                    formatAxis = { metric.axisLabel(it) },
                    // The few bars of a week carry their value; a month is too dense for labels.
                    formatValue = if (bars.size <= 7) { v -> metric.format(v).value } else null,
                    average = metric.average(days), avgColor = accent
                )
            }
        }
    }
}

/** The average a card shows (sleep schedule: bedtime – wake-up, both in the same size). */
internal fun headlineFigure(metric: StatsMetric, days: List<StatsDay>): Formatted {
    if (metric == StatsMetric.SLEEP_SCHEDULE) {
        val nights = days.mapNotNull { metric.segments(it) }
        return Formatted(metric.format(nights.map { it[0] }.average()).value + " – " + metric.format(nights.map { it[1] }.average()).value, "")
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
