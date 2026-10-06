package uk.scimone.diafit.journal.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.core.domain.util.friendlyDateString
import androidx.compose.ui.text.font.FontWeight
import uk.scimone.diafit.journal.presentation.components.DayHeader
import uk.scimone.diafit.journal.presentation.components.JournalEntryCard
import uk.scimone.diafit.journal.presentation.model.GlucoseEpisodeUi
import uk.scimone.diafit.journal.presentation.model.JournalEntryKind
import uk.scimone.diafit.journal.presentation.model.JournalEntryUi
import uk.scimone.diafit.journal.presentation.model.MealEntityUi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun JournalScreen(
    userId: Int,
    onOpenEntry: (JournalEntryUi) -> Unit,
    onAddEntry: () -> Unit,
    viewModel: JournalViewModel = koinViewModel(parameters = { parametersOf(userId) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    var filter by remember { mutableStateOf<JournalEntryKind?>(null) }
    val kinds = JournalEntryKind.availableKinds
    val visible = remember(uiState.entries, filter) {
        uiState.entries.filter { filter == null || it.kind == filter }
    }
    val days = remember(visible) {
        visible.sortedByDescending { it.timeUtc }.groupBy { friendlyDateString(it.timeUtc) }.toList()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RangeChip(uiState.range, viewModel::setRange)
                // A kind filter only earns its space once there is more than one kind of entry to filter by.
                if (kinds.size > 1) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                    kinds.forEach { kind ->
                        FilterChip(selected = filter == kind, onClick = { filter = kind }, label = { Text(kind.pluralLabel) })
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = viewModel::refreshMeals,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                when {
                    uiState.isLoading && days.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                    days.isEmpty() -> EmptyJournal(onAddEntry)

                    else -> LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        days.forEach { (day, entries) ->
                            stickyHeader(key = "day-$day") { DayHeader(day, daySummary(entries)) }
                            items(entries, key = { "${it.kind}-${it.id}" }) { entry ->
                                JournalEntryCard(entry, uiState.target, onClick = { onOpenEntry(entry) })
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM")

private fun JournalRange.label(): String = if (preset == JournalRangePreset.CUSTOM) {
    val (from, to) = days()
    if (from == to) from.format(SHORT_DATE) else "${from.format(SHORT_DATE)} – ${to.format(SHORT_DATE)}"
} else preset.label

/** Time filter chip: opens a menu of presets and a custom date range picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeChip(range: JournalRange, onRange: (JournalRange) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { menuOpen = true },
            label = { Text(range.label()) },
            leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, "Change time range", Modifier.size(18.dp)) }
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            JournalRangePreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label, fontWeight = if (preset == range.preset) FontWeight.Bold else null) },
                    onClick = {
                        menuOpen = false
                        if (preset == JournalRangePreset.CUSTOM) picking = true else onRange(JournalRange(preset))
                    }
                )
            }
        }
    }
    if (picking) {
        val (from, to) = range.days()
        val today = remember { LocalDate.now() }
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = to.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis <= today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
        )
        fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val start = day(state.selectedStartDateMillis!!)
                        val end = state.selectedEndDateMillis?.let(::day) ?: start
                        picking = false
                        onRange(JournalRange(JournalRangePreset.CUSTOM, start, end))
                    }
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }
        ) {
            DateRangePicker(state, Modifier.height(500.dp), showModeToggle = false)
        }
    }
}

/** "3 meals · 142 g carbs · 1 low"; extend per kind as new entry types arrive. */
private fun daySummary(entries: List<JournalEntryUi>): String {
    val meals = entries.filterIsInstance<MealEntityUi>()
    val episodes = entries.filterIsInstance<GlucoseEpisodeUi>()
    val lows = episodes.count { it.episode.isLow }
    val highs = episodes.size - lows
    val parts = buildList {
        if (meals.isNotEmpty()) {
            add("${meals.size} ${if (meals.size == 1) "meal" else "meals"}")  // a multi-course meal counts once
            add("${meals.sumOf { it.carbohydrates }} g carbs")
        }
        if (lows > 0) add("$lows ${if (lows == 1) "low" else "lows"}")
        if (highs > 0) add("$highs ${if (highs == 1) "high" else "highs"}")
    }
    return parts.joinToString(" · ")
}

@Composable
private fun EmptyJournal(onAddEntry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text("Your journal is empty", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Log a meal and see how it moved your glucose.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onAddEntry) { Text("Add first entry") }
    }
}
