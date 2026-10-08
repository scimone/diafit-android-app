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
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import uk.scimone.diafit.journal.presentation.components.formatUnits
import uk.scimone.diafit.journal.presentation.model.GlucoseEpisodeUi
import uk.scimone.diafit.journal.presentation.components.formatDuration
import uk.scimone.diafit.journal.presentation.model.JournalEntryKind
import uk.scimone.diafit.journal.presentation.model.PumpEventUi
import uk.scimone.diafit.journal.presentation.model.PumpEventIcon
import uk.scimone.diafit.ui.theme.Warning
import androidx.compose.ui.graphics.Color
import uk.scimone.diafit.journal.presentation.model.JournalEntryUi
import uk.scimone.diafit.journal.presentation.model.MealEntityUi
import uk.scimone.diafit.journal.presentation.model.PossibleDuplicateUi
import androidx.compose.foundation.shape.RoundedCornerShape

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
    var selectedEvent by remember { mutableStateOf<PumpEventUi?>(null) }
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
                // A kind filter only earns its space once there is more than one kind of entry to filter by.
                if (kinds.size > 1) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                    kinds.forEach { kind ->
                        FilterChip(selected = filter == kind, onClick = { filter = kind }, label = { Text(kind.pluralLabel) })
                    }
                }
                RangeChip(uiState.range, viewModel::setRange)
            }

            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = viewModel::refreshMeals,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                when {
                    uiState.isLoading && days.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                    days.isEmpty() -> EmptyJournal(
                        filter = filter,
                        range = uiState.range,
                        onClearFilter = { filter = null },
                        onResetRange = { viewModel.setRange(JournalRange()) },
                        onAddEntry = onAddEntry
                    )

                    else -> LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        days.forEach { (day, entries) ->
                            stickyHeader(key = "day-$day") { DayHeader(day, daySummary(entries)) }
                            items(entries, key = { "${it.kind}-${it.id}" }) { entry ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    JournalEntryCard(
                                        entry,
                                        uiState.target,
                                        onClick = {
                                            // Profile switches and temporary targets get a detail page; other device events a small dialog.
                                            if (entry is PumpEventUi && !entry.hasDetailPage) selectedEvent = entry else onOpenEntry(entry)
                                        }
                                    )
                                    (entry as? MealEntityUi)?.possibleDuplicate?.let {
                                        PossibleDuplicateStrip(it, onMerge = { viewModel.mergeSuggestion(it.importedId) }, onKeepSeparate = { viewModel.keepSeparate(it.importedId) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        selectedEvent?.let { event ->
            DeviceEventDialog(event, onDismiss = { selectedEvent = null }, onDelete = {
                viewModel.deletePumpEvent(event.ids)
                selectedEvent = null
            })
        }
    }
}

/** Details of a device event, with the option to remove it from the journal. */
@Composable
private fun DeviceEventDialog(event: PumpEventUi, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val time = remember(event.timeUtc) {
        java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(event.timeUtc))
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(event.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(time, style = MaterialTheme.typography.bodyMedium)
                event.notes?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = if (event.icon == PumpEventIcon.WARNING) Warning else Color.Unspecified)
                }
                Text(
                    (event.source?.let { "Imported from $it. " } ?: "") + "Deleting only removes it from Diafit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/** Under a meal card: an AAPS carb entry that might be the same food, to merge or keep apart. */
@Composable
private fun PossibleDuplicateStrip(dup: PossibleDuplicateUi, onMerge: () -> Unit, onKeepSeparate: () -> Unit) {
    val time = remember(dup.timeUtc) { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(dup.timeUtc)) }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Same meal? AAPS carbs ${dup.carbohydrates} g at $time",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onKeepSeparate) { Text("Separate") }
            TextButton(onClick = onMerge) { Text("Merge") }
        }
    }
}

private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM")

private fun JournalRange.label(): String = if (isCustom) {
    val (from, to) = days()
    if (from == to) from.format(SHORT_DATE) else "${from.format(SHORT_DATE)} – ${to.format(SHORT_DATE)}"
} else "Last $DEFAULT_RANGE_DAYS days"

/** Time filter chip: opens the date range picker; a picked range can be cleared back to the default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeChip(range: JournalRange, onRange: (JournalRange) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { picking = true },
        label = { Text(range.label()) },
        leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
        trailingIcon = {
            if (range.isCustom) {
                Icon(
                    Icons.Filled.Close, "Back to last $DEFAULT_RANGE_DAYS days",
                    Modifier.size(18.dp).clip(CircleShape).clickable { onRange(JournalRange()) }
                )
            }
        }
    )
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
                        onRange(JournalRange(start, end))
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
    val bolusEntries = entries.filterIsInstance<uk.scimone.diafit.journal.presentation.model.BolusEntryUi>()
    val boluses = bolusEntries.count { !it.isSmb }
    val smbUnits = bolusEntries.filter { it.isSmb }.sumOf { it.units }
    val lows = episodes.count { it.episode.isLow }
    val highs = episodes.size - lows
    val parts = buildList {
        if (meals.isNotEmpty()) {
            add("${meals.size} ${if (meals.size == 1) "meal" else "meals"}")  // a multi-course meal counts once
            add("${meals.sumOf { it.carbohydrates }} g carbs")
        }
        if (boluses > 0) add("$boluses ${if (boluses == 1) "bolus" else "boluses"}")
        if (smbUnits > 0) add("${formatUnits(smbUnits)} U SMB")
        if (lows > 0) add("$lows ${if (lows == 1) "low" else "lows"}")
        if (highs > 0) add("$highs ${if (highs == 1) "high" else "highs"}")
        entries.filterIsInstance<uk.scimone.diafit.journal.presentation.model.SleepEntryUi>().firstOrNull()
            ?.let { add("${formatDuration(it.asleepMs)} sleep") }
        val workouts = entries.count { it is uk.scimone.diafit.journal.presentation.model.ExerciseEntryUi }
        if (workouts > 0) add("$workouts ${if (workouts == 1) "workout" else "workouts"}")
    }
    return parts.joinToString(" · ")
}

@Composable
private fun EmptyJournal(
    filter: JournalEntryKind?,
    range: JournalRange,
    onClearFilter: () -> Unit,
    onResetRange: () -> Unit,
    onAddEntry: () -> Unit
) {
    // Entries may exist, just not for this filter or period: say so instead of claiming the journal is empty.
    if (filter != null || range.isCustom) {
        val title = if (filter != null) "No ${filter.pluralLabel.lowercase()} in this period" else "Nothing in this period"
        val body = when {
            filter != null && range.isCustom -> "Nothing of this kind was recorded in the days you picked. Try all entries or a longer period."
            filter != null -> "Nothing of this kind was recorded in the last $DEFAULT_RANGE_DAYS days."
            else -> "No entries were recorded in the days you picked."
        }
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (filter != null) FilledTonalButton(onClick = onClearFilter) { Text("Show all entries") }
                if (range.isCustom) FilledTonalButton(onClick = onResetRange) { Text("Last $DEFAULT_RANGE_DAYS days") }
            }
        }
        return
    }
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
            "Meals you log appear here with how they moved your glucose, together with boluses, lows and highs, device changes, sleep and workouts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onAddEntry) { Text("Add first entry") }
    }
}
