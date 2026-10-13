package uk.scimone.diafit.history.presentation

import uk.scimone.diafit.core.presentation.formatStamp
import uk.scimone.diafit.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
            custom = state.custom,
            isLatest = state.page == 0,
            onRange = viewModel::setRange,
            onCustom = viewModel::setCustomPeriod,
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
                    state.errorMessage?.asString() ?: stringResource(R.string.hist_no_history),
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
                            // Today is still running, so it is left out of the overview.
                            itemsIndexed(state.days.filter { it.epochDay != today }, key = { _, day -> day.epochDay }) { index, day ->
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

private fun LocalDate.short(): String =
    formatStamp(atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), "dMMM")

/** Period selector: back/forward arrows around the date range; tapping the range opens the time frame menu (1 week to 3 months). */
@Composable
private fun PeriodHeader(
    first: Long?,
    last: Long?,
    range: HistoryRange,
    custom: CustomPeriod?,
    isLatest: Boolean,
    onRange: (HistoryRange) -> Unit,
    onCustom: (CustomPeriod) -> Unit,
    weekdays: Set<DayOfWeek>,
    onWeekdays: (Set<DayOfWeek>) -> Unit,
    layers: HistoryLayers,
    onLayers: (HistoryLayers) -> Unit,
    onOlder: () -> Unit,
    onNewer: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var customOpen by remember { mutableStateOf(false) }
    if (customOpen) CustomPeriodDialog(custom, onDismiss = { customOpen = false }, onApply = { customOpen = false; onCustom(it) })
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOlder, Modifier.size(40.dp), enabled = custom == null) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.hist_prev_period)) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val label = if (first == null || last == null) "" else
                "${LocalDate.ofEpochDay(first).short()} – ${LocalDate.ofEpochDay(last).short()}"
            TextButton(onClick = { menuOpen = true }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text("  " + if (custom != null) stringResource(R.string.hist_custom) else range.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Filled.ArrowDropDown, stringResource(R.string.hist_change_frame), tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                HistoryRange.entries.forEach { r ->
                    DropdownMenuItem(
                        text = { Text(r.label, fontWeight = if (r == range && custom == null) FontWeight.Bold else null) },
                        onClick = { menuOpen = false; onRange(r) }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hist_custom_menu), fontWeight = if (custom != null) FontWeight.Bold else null) },
                    onClick = { menuOpen = false; customOpen = true }
                )
            }
        }
        IconButton(onClick = onNewer, Modifier.size(40.dp), enabled = !isLatest && custom == null) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.hist_next_period)) }
        FilterButton(weekdays, onWeekdays, layers, onLayers)
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
private fun FilterButton(
    selected: Set<DayOfWeek>,
    onChange: (Set<DayOfWeek>) -> Unit,
    layers: HistoryLayers,
    onLayers: (HistoryLayers) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val active = selected.isNotEmpty()
    Box {
        BadgedBox(badge = { if (active) Badge { Text(selected.size.toString()) } }) {
            IconButton(
                onClick = { open = true },
                modifier = Modifier.size(40.dp),
                colors = if (active) IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary) else IconButtonDefaults.iconButtonColors()
            ) { Icon(Icons.Outlined.FilterList, stringResource(R.string.hist_filter_weekday)) }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.hist_show), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    LayerItem(stringResource(R.string.hist_layer_treatments), layers.treatments) { onLayers(layers.copy(treatments = !layers.treatments)) }
                    LayerItem(stringResource(R.string.panel_activity), layers.activity) { onLayers(layers.copy(activity = !layers.activity)) }
                    LayerItem(stringResource(R.string.datatype_sleep), layers.sleep) { onLayers(layers.copy(sleep = !layers.sleep)) }
                }
                HorizontalDivider()
                Text(stringResource(R.string.hist_weekdays), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                d.getDisplayName(java.time.format.TextStyle.NARROW, androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShortcutChip(stringResource(R.string.hist_workdays), selected == WORK_DAYS) { onChange(if (selected == WORK_DAYS) emptySet() else WORK_DAYS) }
                    ShortcutChip(stringResource(R.string.hist_weekend), selected == WEEKEND) { onChange(if (selected == WEEKEND) emptySet() else WEEKEND) }
                    if (active) ShortcutChip(stringResource(R.string.logs_filter_all), false) { onChange(emptySet()) }
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
