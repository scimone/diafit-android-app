package uk.scimone.diafit.history.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.history.presentation.components.DayTrackRow
import uk.scimone.diafit.history.presentation.components.HistoryTimeAxis
import uk.scimone.diafit.history.presentation.model.DayHistoryUi
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class HistoryTab(val label: String) { CHARTS("Charts"), STATS("Stats") }

/**
 * A selectable time frame (1 week to 3 months) at a glance, in two views: the (sticky) glucose profile chart, and one compact
 * horizon track per day underneath on a shared 0–24 h axis, or the period's statistics with one
 * stat row per day. Tapping a day opens it in full ([onOpenDay]).
 */
@Composable
fun HistoryScreen(
    userId: Int,
    onOpenDay: (epochDay: Long) -> Unit,
    viewModel: HistoryViewModel = koinViewModel(parameters = { parametersOf(userId) })
) {
    val state by viewModel.state.collectAsState()
    val today = remember { LocalDate.now().toEpochDay() }
    var tab by rememberSaveable { mutableStateOf(HistoryTab.CHARTS) }

    Column(Modifier.fillMaxSize()) {
        PeriodHeader(
            days = state.days,
            isLatest = state.page == 0,
            onOlder = viewModel::showOlder,
            onNewer = viewModel::showNewer
        )
        RangeSelector(state.range, viewModel::setRange)
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            HistoryTab.entries.forEach { t ->
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
                    text = { Text(t.label) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.days.isEmpty() -> Text(
                    state.errorMessage ?: "No history yet",
                    Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                tab == HistoryTab.STATS -> HistoryStatsView(state, today, onOpenDay)
                else -> Column(Modifier.fillMaxSize()) {
                    ProfileChartPlaceholder(Modifier.fillMaxWidth().fillMaxHeight(0.3f).padding(horizontal = 8.dp))
                    Spacer(Modifier.height(10.dp))
                    HistoryTimeAxis(Modifier.padding(horizontal = 8.dp))
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 12.dp)
                    ) {
                        itemsIndexed(state.days, key = { _, day -> day.epochDay }) { index, day ->
                            // Newest first: a Sunday starts a new (older) week, so give it some air.
                            val weekBreak = index > 0 && LocalDate.ofEpochDay(day.epochDay).dayOfWeek == DayOfWeek.SUNDAY
                            DayTrackRow(
                                day = day,
                                thresholds = state.thresholds,
                                isToday = day.epochDay == today,
                                onClick = { onOpenDay(day.epochDay) },
                                modifier = if (weekBreak) Modifier.padding(top = 10.dp) else Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}

private val RANGE_FORMAT = DateTimeFormatter.ofPattern("d MMM")

/** Time frame choice: 1 week, 2 weeks, 1 month, 3 months. */
@Composable
private fun RangeSelector(selected: HistoryRange, onSelect: (HistoryRange) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        HistoryRange.entries.forEachIndexed { index, range ->
            SegmentedButton(
                selected = range == selected,
                onClick = { onSelect(range) },
                shape = SegmentedButtonDefaults.itemShape(index, HistoryRange.entries.size),
                icon = {},
                label = { Text(range.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}

/** Period selector: back/forward arrows around the date range. */
@Composable
private fun PeriodHeader(days: List<DayHistoryUi>, isLatest: Boolean, onOlder: () -> Unit, onNewer: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOlder) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous period") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            val label = if (days.isEmpty()) "" else {
                val first = LocalDate.ofEpochDay(days.last().epochDay)
                val last = LocalDate.ofEpochDay(days.first().epochDay)
                "${first.format(RANGE_FORMAT)} – ${last.format(RANGE_FORMAT)}"
            }
            Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
        IconButton(onClick = onNewer, enabled = !isLatest) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next period") }
    }
}

/** Space reserved for the ambulatory glucose profile (median and percentile bands over the window). */
@Composable
private fun ProfileChartPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            Column(Modifier.align(Alignment.TopStart)) {
                Text("Glucose profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("AGP · median and percentile bands", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.AutoMirrored.Outlined.ShowChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    "Placeholder Chart",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
