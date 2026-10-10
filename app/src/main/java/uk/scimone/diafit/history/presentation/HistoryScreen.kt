package uk.scimone.diafit.history.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.history.presentation.components.DayTrackRow
import uk.scimone.diafit.history.presentation.components.HistoryTimeAxis
import uk.scimone.diafit.history.presentation.model.DayHistoryUi
import uk.scimone.diafit.history.presentation.model.HistoryLayers
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter


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
    var layers by rememberSaveable(stateSaver = HistoryLayers.Saver) { mutableStateOf(HistoryLayers()) }

    Column(Modifier.fillMaxSize()) {
        PeriodHeader(
            first = state.periodFirstDay,
            last = state.periodLastDay,
            range = state.range,
            isLatest = state.page == 0,
            onRange = viewModel::setRange,
            weekdays = state.weekdays,
            onWeekdays = viewModel::setWeekdays,
            layers = layers,
            onLayers = { layers = it },
            onOlder = viewModel::showOlder,
            onNewer = viewModel::showNewer
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.days.isEmpty() -> Text(
                    state.errorMessage ?: "No history yet",
                    Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val listState = rememberLazyListState()
                    val density = LocalDensity.current
                    val collapseDistancePx = with(density) { COLLAPSE_SCROLL_DISTANCE.toPx() }
                    // The profile card is full height at the top of the list and shrinks as the days scroll by.
                    val collapse by remember(collapseDistancePx) {
                        derivedStateOf {
                            if (listState.firstVisibleItemIndex > 0) 1f
                            else (listState.firstVisibleItemScrollOffset / collapseDistancePx).coerceIn(0f, 1f)
                        }
                    }
                    val expandedHeight = maxHeight * 0.28f
                    val collapsedHeight = maxOf(MIN_AGP_HEIGHT, expandedHeight * 0.55f)
                    Column(Modifier.fillMaxSize()) {
                        AgpCard(
                            state.agp, state.agpMarkers, state.thresholds, layers, collapse,
                            Modifier.fillMaxWidth().height(lerp(expandedHeight, collapsedHeight, collapse)).padding(horizontal = 8.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        HistoryTimeAxis(Modifier.padding(horizontal = 8.dp))
                        LazyColumn(
                            Modifier.weight(1f),
                            state = listState,
                            // Room to scroll past the collapse, so shrinking the card can never clamp the scroll back to the top.
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 12.dp + (expandedHeight - collapsedHeight))
                        ) {
                            itemsIndexed(state.days, key = { _, day -> day.epochDay }) { index, day ->
                                // Newest first: a Sunday starts a new (older) week, so give it some air.
                                val weekBreak = index > 0 && LocalDate.ofEpochDay(day.epochDay).dayOfWeek == DayOfWeek.SUNDAY
                                DayTrackRow(
                                    day = day,
                                    thresholds = state.thresholds,
                                    isToday = day.epochDay == today,
                                    layers = layers,
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
}

private val COLLAPSE_SCROLL_DISTANCE = 120.dp
private val MIN_AGP_HEIGHT = 112.dp

private val RANGE_FORMAT = DateTimeFormatter.ofPattern("d MMM")

/** Period selector: back/forward arrows around the date range; tapping the range opens the time frame menu (1 week to 3 months). */
@Composable
private fun PeriodHeader(
    first: Long?,
    last: Long?,
    range: HistoryRange,
    isLatest: Boolean,
    onRange: (HistoryRange) -> Unit,
    weekdays: Set<DayOfWeek>,
    onWeekdays: (Set<DayOfWeek>) -> Unit,
    layers: HistoryLayers,
    onLayers: (HistoryLayers) -> Unit,
    onOlder: () -> Unit,
    onNewer: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOlder, Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous period") }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val label = if (first == null || last == null) "" else
                "${LocalDate.ofEpochDay(first).format(RANGE_FORMAT)} – ${LocalDate.ofEpochDay(last).format(RANGE_FORMAT)}"
            TextButton(onClick = { menuOpen = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text("  ${range.label}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Filled.ArrowDropDown, "Change time frame", tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                HistoryRange.entries.forEach { r ->
                    DropdownMenuItem(
                        text = { Text(r.label, fontWeight = if (r == range) FontWeight.Bold else null) },
                        onClick = { menuOpen = false; onRange(r) }
                    )
                }
            }
        }
        IconButton(onClick = onNewer, Modifier.size(40.dp), enabled = !isLatest) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next period") }
        WeekdayFilterButton(weekdays, onWeekdays)
        LayersButton(layers, onLayers)
    }
}


/** Which context layers the charts draw besides glucose, as a menu behind one icon. */
@Composable
private fun LayersButton(layers: HistoryLayers, onChange: (HistoryLayers) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, Modifier.size(40.dp)) { Icon(Icons.Outlined.Layers, "Choose layers") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LayerItem("Glucose", true, enabled = false) {}
            LayerItem("Meals and insulin", layers.treatments) { onChange(layers.copy(treatments = !layers.treatments)) }
            LayerItem("Activity", layers.activity) { onChange(layers.copy(activity = !layers.activity)) }
            LayerItem("Sleep", layers.sleep) { onChange(layers.copy(sleep = !layers.sleep)) }
        }
    }
}

@Composable
private fun LayerItem(label: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
        enabled = enabled,
        onClick = onToggle
    )
}

private val WEEKDAYS = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
private val WORK_DAYS = WEEKDAYS.toSet()

/**
 * Weekday filter as one icon in the period header (tonal, with the number of chosen days, once active). It opens a small
 * popup with seven round day toggles (none chosen = all days) and shortcuts for Mon–Fri and Sat–Sun, which a second tap clears.
 */
@Composable
private fun WeekdayFilterButton(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val active = selected.isNotEmpty()
    Box {
        BadgedBox(badge = { if (active) Badge { Text(selected.size.toString()) } }) {
            IconButton(
                onClick = { open = true },
                modifier = Modifier.size(40.dp),
                colors = if (active) IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary) else IconButtonDefaults.iconButtonColors()
            ) { Icon(Icons.Outlined.FilterList, "Filter by weekday") }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Weekdays", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        val on = d in selected
                        val shape = androidx.compose.foundation.shape.CircleShape
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(shape)
                                .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .border(1.dp, if (on) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, shape)
                                .clickable { onChange(if (on) selected - d else selected + d) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                d.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShortcutChip("Mon–Fri", selected == WORK_DAYS) { onChange(if (selected == WORK_DAYS) emptySet() else WORK_DAYS) }
                    ShortcutChip("Sat–Sun", selected == WEEKEND) { onChange(if (selected == WEEKEND) emptySet() else WEEKEND) }
                    if (active) ShortcutChip("All", false) { onChange(emptySet()) }
                }
            }
        }
    }
}

@Composable
private fun ShortcutChip(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .height(28.dp)
            .clip(shape)
            .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .border(1.dp, if (active) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
