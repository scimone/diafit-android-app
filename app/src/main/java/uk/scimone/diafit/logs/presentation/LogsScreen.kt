package uk.scimone.diafit.logs.presentation

import uk.scimone.diafit.core.presentation.AppStrings
import uk.scimone.diafit.core.presentation.formatStamp
import uk.scimone.diafit.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import uk.scimone.diafit.logs.presentation.components.DayHeader
import uk.scimone.diafit.logs.presentation.components.LogsEntryCard
import uk.scimone.diafit.logs.presentation.components.formatUnits
import uk.scimone.diafit.logs.presentation.model.CarbCourse
import uk.scimone.diafit.logs.presentation.model.GlucoseEpisodeUi
import uk.scimone.diafit.logs.presentation.components.formatDuration
import uk.scimone.diafit.logs.presentation.model.LogsEntryKind
import uk.scimone.diafit.logs.presentation.model.PumpEventUi
import uk.scimone.diafit.logs.presentation.model.PumpEventIcon
import uk.scimone.diafit.ui.theme.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uk.scimone.diafit.logs.presentation.components.LogsGlucoseGraph
import uk.scimone.diafit.logs.presentation.components.BOLUS_REACH_MS
import uk.scimone.diafit.logs.presentation.components.CarbCurve
import uk.scimone.diafit.logs.presentation.components.GraphMarker
import uk.scimone.diafit.logs.presentation.model.SleepEntryUi
import uk.scimone.diafit.logs.presentation.model.ExerciseEntryUi
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.logs.presentation.components.LOGS_ANCHOR_POINT
import uk.scimone.diafit.logs.presentation.components.LOGS_ANCHOR_STRETCH
import uk.scimone.diafit.logs.presentation.components.LOGS_GRAPH_WINDOW_MIN
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.logs.presentation.model.LogsEntryUi
import uk.scimone.diafit.logs.presentation.model.MealEntityUi
import uk.scimone.diafit.logs.presentation.model.PossibleDuplicateUi
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogsScreen(
    userId: Int,
    onOpenEntry: (LogsEntryUi) -> Unit,
    onAddEntry: () -> Unit,
    viewModel: LogsViewModel = koinViewModel(parameters = { parametersOf(userId) })
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it.asString(errorContext)) }
    }

    var filter by remember { mutableStateOf<LogsEntryKind?>(null) }
    var selectedEvent by remember { mutableStateOf<PumpEventUi?>(null) }
    val kinds = LogsEntryKind.availableKinds
    val visible = remember(uiState.entries, filter) {
        uiState.entries.filter { filter == null || it.kind == filter }
    }
    val days = remember(visible) {
        visible.sortedByDescending { it.timeUtc }.groupBy { friendlyDateString(it.timeUtc) }.toList()
    }

    // --- Graph <-> list sync -------------------------------------------------------------------------
    // The highlighted entry is `selected`; the graph's marker sits on its timestamp. Scrolling the list moves
    // the highlight to the first entry below the sticky day header; dragging or tapping the graph picks the
    // nearest entry and scrolls the list to it.
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val glucose by viewModel.glucose.collectAsState()
    val baseMs = remember { System.currentTimeMillis() / 60_000L * 60_000L - 1000L * 1440 * 60_000L }
    fun minutesOf(ms: Long) = ((ms - baseMs) / 60_000.0).toFloat()
    val center = remember { Animatable(minutesOf(System.currentTimeMillis())) }
    var headerPx by remember { mutableIntStateOf(0) }
    var listHeightPx by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(0) }
    var graphDriving by remember { mutableStateOf(false) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    /** Entries in list order (newest first) and the LazyColumn item index of each. */
    val flat = remember(days) {
        var index = 0
        val entries = ArrayList<LogsEntryUi>()
        val rows = ArrayList<Int>()
        days.forEach { (_, list) ->
            index++  // the day header
            list.forEach { entries += it; rows += index++ }
        }
        FlatEntries(entries, rows.toIntArray())
    }
    val markers = remember(flat) { flat.entries.map { it.toGraphMarker() } }
    val selectedIndex = selected.coerceIn(0, (flat.entries.size - 1).coerceAtLeast(0))
    val selectedEntry = flat.entries.getOrNull(selectedIndex)
    val anchorFraction by animateFloatAsState(
        if (markers.getOrNull(selectedIndex)?.endMs != null) LOGS_ANCHOR_STRETCH else LOGS_ANCHOR_POINT, tween(220), label = "anchor"
    )

    suspend fun moveCenterTo(ms: Long, animate: Boolean) {
        val target = minutesOf(ms)
        if (animate && kotlin.math.abs(target - center.value) < LOGS_GRAPH_WINDOW_MIN) center.animateTo(target, tween(220))
        else center.snapTo(target)
    }

    fun scrollListTo(i: Int) {
        scrollJob?.cancel()
        scrollJob = scope.launch { listState.animateScrollToItem(flat.rowIndex[i], -headerPx) }
    }

    fun nearestTo(minutes: Float): Int {
        var best = 0
        var bestDist = Float.MAX_VALUE
        flat.entries.forEachIndexed { i, e ->
            val d = kotlin.math.abs(minutesOf(e.graphTimeUtc()) - minutes)
            if (d < bestDist) { bestDist = d; best = i }
        }
        return best
    }

    /** Graph-driven selection of entry [i]: the marker glides there and the list follows. */
    fun jumpTo(i: Int) {
        if (flat.entries.isEmpty()) return
        graphDriving = true
        selected = i
        scrollListTo(i)
        scope.launch {
            moveCenterTo(flat.entries[i].graphTimeUtc(), animate = true)
            scrollJob?.join()
            graphDriving = false
        }
    }

    // List -> graph: the first entry under the sticky header is the highlighted one.
    LaunchedEffect(flat) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull {
                (it.key as? String)?.startsWith("day-") == false && it.offset + it.size / 2 >= headerPx
            }?.index
        }.filterNotNull().distinctUntilChanged().collectLatest { itemIndex ->
            if (graphDriving) return@collectLatest
            val i = flat.rowIndex.indexOf(itemIndex)
            if (i < 0) return@collectLatest
            selected = i
            moveCenterTo(flat.entries[i].graphTimeUtc(), animate = true)
        }
    }

    // Load the glucose around the marker as it moves (day-sized pieces), and refresh the current day every minute.
    LaunchedEffect(Unit) {
        snapshotFlow { (baseMs + center.value.toLong() * 60_000L) / (30 * 60_000L) }
            .distinctUntilChanged()
            .collect { viewModel.ensureGlucose(baseMs + center.value.toLong() * 60_000L) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            viewModel.ensureGlucose(baseMs + center.value.toLong() * 60_000L)
        }
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
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.logs_filter_all)) })
                    kinds.forEach { kind ->
                        FilterChip(selected = filter == kind, onClick = { filter = kind }, label = { Text(kind.pluralLabel) })
                    }
                }
                RangeChip(uiState.range, viewModel::setRange)
            }

            if (flat.entries.isNotEmpty()) {
                LogsGlucoseGraph(
                    centerMinutes = { center.value },
                    anchorFraction = { anchorFraction },
                    baseMs = baseMs,
                    glucose = glucose,
                    lower = uiState.target.lowerBound,
                    upper = uiState.target.upperBound,
                    markers = markers,
                    selectedMarker = selectedIndex,
                    allCarbCourses = uiState.carbCourses,
                    allDoses = uiState.doses,
                    onDragStart = {
                        graphDriving = true
                        scope.launch { center.stop() }
                    },
                    onDrag = { deltaMin ->
                        scope.launch {
                            val first = flat.entries.minOf { minutesOf(it.graphTimeUtc()) }
                            val last = flat.entries.maxOf { minutesOf(it.graphTimeUtc()) }
                            center.snapTo((center.value + deltaMin).coerceIn(first, last))
                            val n = nearestTo(center.value)
                            if (n != selected) {
                                selected = n
                                scrollListTo(n)
                            }
                        }
                    },
                    onDragEnd = { jumpTo(selectedIndex) },
                    onTap = { offsetMin -> jumpTo(nearestTo(center.value + offsetMin)) },
                    modifier = Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = viewModel::refreshMeals,
                modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { listHeightPx = it.height }
            ) {
                when {
                    uiState.isLoading && days.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                    days.isEmpty() -> EmptyLogs(
                        filter = filter,
                        range = uiState.range,
                        onClearFilter = { filter = null },
                        onResetRange = { viewModel.setRange(LogsRange()) },
                        onAddEntry = onAddEntry
                    )

                    else -> LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        state = listState,
                        // Room below the last entry so that every entry (the oldest too) can reach the top and be highlighted.
                        contentPadding = PaddingValues(bottom = with(density) { (listHeightPx - headerPx).toDp() - 96.dp }.coerceAtLeast(24.dp)),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        days.forEach { (day, entries) ->
                            stickyHeader(key = "day-$day") { DayHeader(day, daySummary(entries), Modifier.onSizeChanged { headerPx = it.height }) }
                            items(entries, key = { "${it.kind}-${it.id}" }) { entry ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    LogsEntryCard(
                                        entry,
                                        uiState.target,
                                        modifier = if (entry === selectedEntry) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier,
                                        onAddPhoto = { uri -> viewModel.addPhoto(entry.id, uri) },
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

/** How an entry shows on the graph: its time, or the stretch it covers, in its kind's colour. */
/** Where the graph centres on an entry: the middle of a stretch (sleep, workout, low/high), else its time. */
private fun LogsEntryUi.graphTimeUtc(): Long = when (this) {
    is GlucoseEpisodeUi -> (episode.startUtc + episode.endUtc) / 2
    is SleepEntryUi -> (startUtc + timeUtc) / 2
    is ExerciseEntryUi -> (timeUtc + endUtc) / 2
    else -> timeUtc
}

private fun LogsEntryUi.toGraphMarker(): GraphMarker = when (this) {
    is GlucoseEpisodeUi -> GraphMarker(episode.startUtc, episode.endUtc, if (episode.isLow) BelowRange else AboveRange)
    is SleepEntryUi -> GraphMarker(startUtc, timeUtc, kind.accent)
    is ExerciseEntryUi -> GraphMarker(timeUtc, endUtc, kind.accent)
    is MealEntityUi -> GraphMarker(
        timeUtc, null, kind.accent,
        afterMs = carbCourses.maxOfOrNull { it.curve.carbsEndTimeUtc() } ?: (endTimeUtc + impactType.durationMinutes * 60_000L),
        extraColor = Bolus.takeIf { (insulinUnits ?: 0.0) > 0.0 },
        peakMs = peakTimeUtc?.takeIf { peakMgdl != null && (startMgdl == null || peakMgdl > startMgdl) },
        peakMgdl = peakMgdl,
        carbs = carbohydrates.takeIf { it > 0 }?.let { g ->
            CarbCurve(carbCourses)
        }
    )
    is uk.scimone.diafit.logs.presentation.model.BolusEntryUi -> GraphMarker(
        timeUtc, null, kind.accent,
        afterMs = (parts.maxOfOrNull { it.timeUtc } ?: timeUtc) + BOLUS_REACH_MS,
        areaStartMs = parts.minOfOrNull { it.timeUtc } ?: timeUtc,
        insulin = true,
        doses = parts.ifEmpty { listOf(uk.scimone.diafit.logs.presentation.model.BolusPartUi(timeUtc, units, isSmb)) }
    )
    else -> GraphMarker(timeUtc, null, kind.accent)
}

/** The visible entries in list order, and the LazyColumn item index of each. */
private class FlatEntries(val entries: List<LogsEntryUi>, val rowIndex: IntArray)

/** Details of a device event, with the option to remove it from the logs. */
@Composable
private fun DeviceEventDialog(event: PumpEventUi, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val time = remember(event.timeUtc, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]) { formatStamp(event.timeUtc, "EEEdMMMHm") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(event.titleRes?.let { stringResource(it) } ?: event.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(time, style = MaterialTheme.typography.bodyMedium)
                event.notes?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = if (event.icon == PumpEventIcon.WARNING) Warning else Color.Unspecified)
                }
                Text(
                    event.source?.let { stringResource(R.string.logs_device_imported_note, it) } ?: stringResource(R.string.logs_device_delete_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDelete) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } }
    )
}

/** Under a meal card: an AAPS carb entry that might be the same food, to merge or keep apart. */
@Composable
private fun PossibleDuplicateStrip(dup: PossibleDuplicateUi, onMerge: () -> Unit, onKeepSeparate: () -> Unit) {
    val time = remember(dup.timeUtc) { formatStamp(dup.timeUtc, "Hm") }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.logs_dup_text, dup.carbohydrates, time),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onKeepSeparate) { Text(stringResource(R.string.logs_dup_separate)) }
            TextButton(onClick = onMerge) { Text(stringResource(R.string.logs_dup_merge)) }
        }
    }
}

private fun LocalDate.short(): String =
    formatStamp(atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), "dMMM")

private fun LogsRange.label(): String = if (isCustom) {
    val (from, to) = days()
    if (from == to) from.short() else "${from.short()} – ${to.short()}"
} else AppStrings.get(R.string.logs_range_last_days, DEFAULT_RANGE_DAYS)

/** Time filter chip: opens the date range picker; a picked range can be cleared back to the default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeChip(range: LogsRange, onRange: (LogsRange) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { picking = true },
        label = { Text(range.label()) },
        leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
        trailingIcon = {
            if (range.isCustom) {
                Icon(
                    Icons.Filled.Close, stringResource(R.string.logs_range_back, DEFAULT_RANGE_DAYS),
                    Modifier.size(18.dp).clip(CircleShape).clickable { onRange(LogsRange()) }
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
                        onRange(LogsRange(start, end))
                    }
                ) { Text(stringResource(R.string.common_apply)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.common_cancel)) } }
        ) {
            DateRangePicker(state, Modifier.height(500.dp), showModeToggle = false)
        }
    }
}

/** "3 meals · 142 g carbs · 1 low"; extend per kind as new entry types arrive. */
private fun daySummary(entries: List<LogsEntryUi>): String {
    val meals = entries.filterIsInstance<MealEntityUi>()
    val episodes = entries.filterIsInstance<GlucoseEpisodeUi>()
    val bolusEntries = entries.filterIsInstance<uk.scimone.diafit.logs.presentation.model.BolusEntryUi>()
    val boluses = bolusEntries.count { !it.isSmb }
    val smbUnits = bolusEntries.filter { it.isSmb }.sumOf { it.units }
    val lows = episodes.count { it.episode.isLow }
    val highs = episodes.size - lows
    val parts = buildList {
        if (meals.isNotEmpty()) {
            add(AppStrings.plural(R.plurals.logs_summary_meals, meals.size, meals.size))  // a multi-course meal counts once
            add(AppStrings.get(R.string.logs_summary_carbs, meals.sumOf { it.carbohydrates }))
        }
        if (boluses > 0) add(AppStrings.plural(R.plurals.logs_summary_boluses, boluses, boluses))
        if (smbUnits > 0) add(AppStrings.get(R.string.logs_summary_smb, formatUnits(smbUnits)))
        if (lows > 0) add(AppStrings.plural(R.plurals.logs_summary_lows, lows, lows))
        if (highs > 0) add(AppStrings.plural(R.plurals.logs_summary_highs, highs, highs))
        entries.filterIsInstance<uk.scimone.diafit.logs.presentation.model.SleepEntryUi>().firstOrNull()
            ?.let { add(AppStrings.get(R.string.logs_summary_sleep, formatDuration(it.asleepMs))) }
        val workouts = entries.count { it is uk.scimone.diafit.logs.presentation.model.ExerciseEntryUi }
        if (workouts > 0) add(AppStrings.plural(R.plurals.logs_summary_workouts, workouts, workouts))
    }
    return parts.joinToString(" · ")
}

@Composable
private fun EmptyLogs(
    filter: LogsEntryKind?,
    range: LogsRange,
    onClearFilter: () -> Unit,
    onResetRange: () -> Unit,
    onAddEntry: () -> Unit
) {
    // Entries may exist, just not for this filter or period: say so instead of claiming the logs is empty.
    if (filter != null || range.isCustom) {
        val title = if (filter != null) stringResource(filter.emptyRes) else stringResource(R.string.logs_empty_title_period)
        val body = when {
            filter != null && range.isCustom -> stringResource(R.string.logs_empty_body_kind_custom)
            filter != null -> stringResource(R.string.logs_empty_body_kind, DEFAULT_RANGE_DAYS)
            else -> stringResource(R.string.logs_empty_body_period)
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
                if (filter != null) FilledTonalButton(onClick = onClearFilter) { Text(stringResource(R.string.logs_show_all)) }
                if (range.isCustom) FilledTonalButton(onClick = onResetRange) { Text(stringResource(R.string.logs_range_last_days, DEFAULT_RANGE_DAYS)) }
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
        Text(stringResource(R.string.logs_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.logs_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onAddEntry) { Text(stringResource(R.string.logs_add_first)) }
    }
}
