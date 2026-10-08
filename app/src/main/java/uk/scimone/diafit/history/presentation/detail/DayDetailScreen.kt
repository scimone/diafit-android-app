package uk.scimone.diafit.history.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.core.domain.model.DayGlucoseStats
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.core.domain.model.GlucoseZone
import uk.scimone.diafit.journal.presentation.components.JournalEntryCard
import uk.scimone.diafit.journal.presentation.components.formatDuration
import uk.scimone.diafit.journal.presentation.components.formatUnits
import uk.scimone.diafit.journal.presentation.model.BolusEntryUi
import uk.scimone.diafit.journal.presentation.model.PumpEventUi
import uk.scimone.diafit.journal.presentation.model.SleepEntryUi
import uk.scimone.diafit.journal.presentation.model.ExerciseEntryUi
import uk.scimone.diafit.journal.presentation.model.GlucoseEpisodeUi
import uk.scimone.diafit.journal.presentation.model.MealEntityUi
import uk.scimone.diafit.core.domain.model.ActivityDayStats
import uk.scimone.diafit.core.domain.model.SleepStage
import uk.scimone.diafit.ui.theme.Activity
import uk.scimone.diafit.ui.theme.Sleep
import uk.scimone.diafit.ui.theme.SleepAwake
import uk.scimone.diafit.ui.theme.SleepDeep
import uk.scimone.diafit.ui.theme.SleepLight
import uk.scimone.diafit.ui.theme.SleepRem
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.InRange
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Below this share of the (elapsed) day covered by readings, the day's numbers are flagged as based on little data. */
private const val MIN_DAY_COVERAGE = 0.7

/** How far back the day pager reaches. */
private const val MAX_DAYS_BACK = 3650

/** The three views of a day. */
private enum class DayTab(val label: String) { CHARTS("Charts"), STATS("Stats"), JOURNAL("Journal") }

/**
 * One day in full, opened from a History row, in three tabs: stats, the Home-style charts with the
 * meal photo strip, and the day's journal (meals and lows/highs). Swipe sideways (or use the arrows)
 * to move between days; on the Charts tab swiping is left to the charts (pan/zoom), the arrows still work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    userId: Int,
    initialEpochDay: Long,
    onBack: () -> Unit,
    onOpenMeal: (Int) -> Unit
) {
    val today = remember { LocalDate.now().toEpochDay() }
    val pageCount = MAX_DAYS_BACK + 1
    fun epochDayOf(page: Int) = today - (pageCount - 1 - page)
    val pagerState = rememberPagerState(initialPage = (pageCount - 1 - (today - initialEpochDay)).toInt().coerceIn(0, pageCount - 1)) { pageCount }
    val scope = rememberCoroutineScope()
    val shown = LocalDate.ofEpochDay(epochDayOf(pagerState.currentPage))
    // The selected tab stays the same while moving between days.
    var tab by rememberSaveable { mutableStateOf(DayTab.CHARTS) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(relativeDayName(shown, LocalDate.ofEpochDay(today)))
                            Text(
                                shown.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = {
                        IconButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                            enabled = pagerState.currentPage > 0
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
                        IconButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                            enabled = pagerState.currentPage < pageCount - 1
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
                    }
                )
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    DayTab.entries.forEach { t ->
                        Tab(
                            selected = tab == t,
                            onClick = { tab = t },
                            text = { Text(t.label) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            userScrollEnabled = tab != DayTab.CHARTS,
            key = { epochDayOf(it) }
        ) { page ->
            DayPage(userId, epochDayOf(page), tab, onOpenMeal)
        }
    }
}

private fun relativeDayName(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
}

@Composable
private fun DayPage(userId: Int, epochDay: Long, tab: DayTab, onOpenMeal: (Int) -> Unit) {
    val viewModel: DayDetailViewModel = koinViewModel(key = "history-day-$epochDay", parameters = { parametersOf(userId, epochDay) })
    val state by viewModel.state.collectAsState()

    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.isEmpty -> EmptyNote(state.errorMessage ?: "No glucose readings, meals, insulin or activity on this day.")
        else -> when (tab) {
            DayTab.STATS -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item { SummaryCard(state) }
                state.activityStats?.let { stats -> item { Spacer(Modifier.height(14.dp)); ActivityCard(stats) } }
            }
            DayTab.CHARTS -> DayCharts(state, onOpenMeal)
            DayTab.JOURNAL -> if (state.entries.isEmpty()) EmptyNote("No meals, boluses, lows, highs, sleep or exercise on this day.") else LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.entries, key = { "${it.kind}-${it.id}" }) { entry ->
                    JournalEntryCard(
                        entry,
                        state.target,
                        onClick = when (entry) {
                            is MealEntityUi -> ({ onOpenMeal(entry.id) })
                            is GlucoseEpisodeUi, is BolusEntryUi, is PumpEventUi, is SleepEntryUi, is ExerciseEntryUi -> null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- summary

@Composable
private fun SummaryCard(state: DayDetailState) {
    val stats = state.stats
    Section {
        if (stats == null) {
            Text("No glucose readings on this day", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Time in range", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${percent(stats.inRangeShare)}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (stats.inRangeShare >= 0.7) InRange else MaterialTheme.colorScheme.onSurface
                        )
                        Text("%", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 5.dp, start = 2.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${state.thresholds.low}–${state.thresholds.high} mg/dL",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Judge coverage against the part of the day that has happened (today isn't over yet).
                    val elapsed = (minOf(System.currentTimeMillis(), state.dayEndUtc) - state.dayStartUtc).coerceAtLeast(1)
                    val sparse = stats.coveredMs < elapsed * MIN_DAY_COVERAGE
                    Text(
                        if (sparse) "Only ${formatDuration(stats.coveredMs)} of sensor data" else "Sensor data ${formatDuration(stats.coveredMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (sparse) FontWeight.SemiBold else null,
                        color = if (sparse) Carbs else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TimeInRangeBar(stats)
            ZoneBreakdown(stats, state.thresholds)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth()) {
                Metric("Average", "${stats.meanMgdl.toInt()}", "mg/dL", Modifier.weight(1f))
                Metric("Variability", "${stats.cvPercent.toInt()}", "% CV", Modifier.weight(1f), valueColor = if (stats.cvPercent > 36) AboveRange else null)
                Metric("Range", "${stats.minMgdl}–${stats.maxMgdl}", "mg/dL", Modifier.weight(1.3f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Metric("Carbs", "${state.totalCarbs}", "g", Modifier.weight(1f), valueColor = Carbs)
            Metric("Insulin", formatUnits(state.totalInsulin), "U", Modifier.weight(1f), valueColor = Bolus)
            if (state.smbUnits > 0) {
                Metric("Bolus · SMB", "${formatUnits(state.bolusUnits)} · ${formatUnits(state.smbUnits)}", "U", Modifier.weight(1.3f))
            } else {
                Metric("Boluses", "${state.bolusCount}", "", Modifier.weight(1.3f))
            }
        }
    }
}

internal val ZONE_ORDER = listOf(GlucoseZone.VERY_LOW, GlucoseZone.LOW, GlucoseZone.IN_RANGE, GlucoseZone.HIGH, GlucoseZone.VERY_HIGH)

/** Very deep zones get the full colour, the mild ones a lighter tint of it. */
internal fun zoneFill(zone: GlucoseZone): Color = when (zone) {
    GlucoseZone.VERY_LOW -> BelowRange
    GlucoseZone.LOW -> BelowRange.copy(alpha = 0.6f)
    GlucoseZone.IN_RANGE -> InRange
    GlucoseZone.HIGH -> AboveRange.copy(alpha = 0.6f)
    GlucoseZone.VERY_HIGH -> AboveRange
}

/** Stacked bar of the five zones, low on the left. */
@Composable
internal fun TimeInRangeBar(stats: DayGlucoseStats) {
    Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ZONE_ORDER.forEach { zone ->
            val share = stats.share(zone)
            if (share > 0.0) Box(Modifier.weight(share.toFloat()).fillMaxHeight().background(zoneFill(zone)))
        }
    }
}

@Composable
private fun ZoneBreakdown(stats: DayGlucoseStats, t: GlucoseThresholds) {
    val rows = listOf(
        Triple(GlucoseZone.VERY_HIGH, "Very high", ">${t.veryHigh}"),
        Triple(GlucoseZone.HIGH, "High", "${t.high + 1}–${t.veryHigh}"),
        Triple(GlucoseZone.IN_RANGE, "In range", "${t.low}–${t.high}"),
        Triple(GlucoseZone.LOW, "Low", "${t.veryLow}–${t.low - 1}"),
        Triple(GlucoseZone.VERY_LOW, "Very low", "<${t.veryLow}")
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { (zone, label, range) ->
            val share = stats.share(zone)
            val faded = share == 0.0
            val textColor = if (faded) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(zoneFill(zone).copy(alpha = if (faded) 0.3f else 1f), RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(10.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = textColor, modifier = Modifier.width(84.dp))
                Text(range, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (faded) 0.5f else 1f), modifier = Modifier.weight(1f))
                Text(
                    formatDuration((share * stats.coveredMs).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (faded) 0.5f else 1f)
                )
                Text(
                    "${percent(share)}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    modifier = Modifier.width(48.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}

@Composable
internal fun Metric(label: String, value: String, unit: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(" $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}


// ---------------------------------------------------------------- shared bits

@Composable
private fun Section(contentSpacing: androidx.compose.ui.unit.Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = if (contentSpacing == 0.dp) 4.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(contentSpacing), content = content)
    }
}

internal fun percent(share: Double): Int = Math.round(share * 100).toInt()


// ---------------------------------------------------------------- activity

/** Steps, sleep, exercise and heart rate of the day (from Health Connect). */
@Composable
private fun ActivityCard(stats: ActivityDayStats) {
    Section {
        Text("Activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth()) {
            Metric("Steps", "%,d".format(stats.steps), "", Modifier.weight(1f), valueColor = Activity.takeIf { stats.steps > 0 })
            Metric(if (stats.exerciseCount > 1) "Exercise · ${stats.exerciseCount}×" else "Exercise", if (stats.exerciseCount == 0) "–" else formatDuration(stats.exerciseMs), "", Modifier.weight(1f), valueColor = Activity.takeIf { stats.exerciseCount > 0 })
            Metric("Sleep", if (stats.hasSleep) formatDuration(stats.sleepMs) else "–", "", Modifier.weight(1f), valueColor = Sleep.takeIf { stats.hasSleep })
        }
        if (stats.hasHeartRate) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth()) {
                Metric("Heart rate · avg", "${stats.avgBpm}", "bpm", Modifier.weight(1f))
                Metric("Resting", "${stats.restingBpm}", "bpm", Modifier.weight(1f))
                Metric("Max", "${stats.maxBpm}", "bpm", Modifier.weight(1f))
            }
        }
        if (stats.hasSleep && stats.stageMs.keys.any { it != SleepStage.SLEEPING }) {
            val order = listOf(SleepStage.DEEP, SleepStage.LIGHT, SleepStage.REM, SleepStage.AWAKE)
            val total = order.sumOf { stats.stageMs[it] ?: 0L }.coerceAtLeast(1L)
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                order.forEach { stage ->
                    val ms = stats.stageMs[stage] ?: return@forEach
                    Box(Modifier.weight(ms / total.toFloat()).fillMaxHeight().background(stageColor(stage)))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                order.forEach { stage ->
                    val ms = stats.stageMs[stage] ?: return@forEach
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(stageColor(stage), RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text("${stage.label} ${formatDuration(ms)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

internal fun stageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.AWAKE -> SleepAwake
    SleepStage.REM -> SleepRem
    SleepStage.LIGHT -> SleepLight
    SleepStage.DEEP -> SleepDeep
    SleepStage.SLEEPING -> Sleep
}
