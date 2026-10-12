package uk.scimone.diafit.settings.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.data.backfill.BackfillStatus
import uk.scimone.diafit.core.domain.model.TimeRange
import uk.scimone.diafit.settings.domain.model.Connector
import uk.scimone.diafit.settings.domain.model.DataType
import uk.scimone.diafit.settings.domain.model.connectorsBackfilling
import uk.scimone.diafit.settings.domain.model.connectorsProviding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DAY_MS = 86_400_000L

/** How far back the user wants to fill in: a preset number of days, or an explicit first–last day. */
private sealed interface BackfillSpan {
    data class LastDays(val days: Int) : BackfillSpan
    data class Custom(val from: LocalDate, val to: LocalDate) : BackfillSpan
}

private fun BackfillSpan.toRange(): TimeRange {
    val zone = ZoneId.systemDefault()
    return when (this) {
        is BackfillSpan.LastDays -> TimeRange(
            LocalDate.now().minusDays(days.toLong() - 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            System.currentTimeMillis()
        )
        is BackfillSpan.Custom -> TimeRange(
            from.atStartOfDay(zone).toInstant().toEpochMilli(),
            minOf(to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        )
    }
}

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

private fun BackfillSpan.label(): String = when (this) {
    is BackfillSpan.LastDays -> "$days days"
    is BackfillSpan.Custom -> "${from.format(DAY_FORMAT)} – ${to.format(DAY_FORMAT)}"
}

/** One collapsed row per data type: which connector feeds it, and (expanded) the choice plus "fill in past data". */
@Composable
internal fun DataSourcesList(
    state: SettingsState,
    onSelect: (DataType, Connector?) -> Unit,
    onBackfill: (DataType, Connector, TimeRange) -> Unit,
    onDismissBackfill: () -> Unit,
    missingRanges: suspend (DataType, TimeRange) -> List<TimeRange>,
    onAllowHistory: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    DataType.values().forEach { type ->
        HorizontalDivider()
        val open = expanded == type.name
        val selected = state.selections[type]
        val available = connectorsProviding(type).filter { it in state.enabledConnectors }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = if (open) null else type.name }
                .padding(vertical = 10.dp)
        ) {
            Text(type.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(
                when {
                    available.isEmpty() -> "Not set up"
                    selected == null -> "Off"
                    else -> selected.displayName
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            )
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(open) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                if (available.isEmpty()) {
                    Text(
                        "No connector set up for this. Connect ${connectorsProviding(type).joinToString(", ", transform = Connector::displayName)} above.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    available.forEach { connector ->
                        // Tapping the ticked box again switches this data off.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(type, if (connector == selected) null else connector) }
                        ) {
                            Checkbox(checked = connector == selected, onCheckedChange = { onSelect(type, if (it) connector else null) })
                            Text(connector.displayName)
                        }
                    }
                }
                val sources = connectorsBackfilling(type).filter { it in state.enabledConnectors }
                if (sources.isNotEmpty() && selected != null) {
                    BackfillPanel(type, sources, state, onBackfill, onDismissBackfill, missingRanges, onAllowHistory)
                } else if (selected != null && connectorsBackfilling(type).isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "To fill in past data, connect ${connectorsBackfilling(type).joinToString(" or ", transform = Connector::displayName)} above.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BackfillPanel(
    type: DataType,
    sources: List<Connector>,
    state: SettingsState,
    onBackfill: (DataType, Connector, TimeRange) -> Unit,
    onDismiss: () -> Unit,
    missingRanges: suspend (DataType, TimeRange) -> List<TimeRange>,
    onAllowHistory: () -> Unit
) {
    var source by remember(sources) { mutableStateOf(sources.first()) }
    var span by remember { mutableStateOf<BackfillSpan>(BackfillSpan.LastDays(14)) }
    // Fixed per choice: "now" must not be re-read on every recomposition, or the gap check restarts in a loop.
    val range = remember(span) { span.toRange() }
    val status = state.backfill
    val running = status is BackfillStatus.Running || status is BackfillStatus.RunningAll
    val mine = status.takeIf {
        (it is BackfillStatus.Running && it.type == type) || (it is BackfillStatus.Done && it.type == type) ||
            (it is BackfillStatus.Failed && it.type == type)
    }

    // What would actually be fetched; recomputed when the range changes or a backfill finishes.
    val gaps by produceState<List<TimeRange>?>(null, type, range.start, range.end, status is BackfillStatus.Done) {
        value = null
        value = missingRanges(type, range)
    }
    val fetchDays = gaps?.sumOf { it.length }?.let { (it + DAY_MS - 1) / DAY_MS }
    val totalDays = ((range.end - range.start + DAY_MS - 1) / DAY_MS).coerceAtLeast(1)
    val needsHistory = source == Connector.HEALTH_CONNECT && !state.healthConnect.historyGranted &&
        range.start < System.currentTimeMillis() - 29 * DAY_MS

    Spacer(Modifier.height(8.dp))
    Text("Fill in past data", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (sources.size > 1) {
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sources.forEach { c ->
                FilterChip(selected = c == source, onClick = { source = c }, label = { Text("From ${c.displayName}") })
            }
        }
    } else {
        Text("From ${source.displayName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(4.dp))
    SpanChooser(span) { span = it }
    Spacer(Modifier.height(4.dp))
    Text(
        when {
            gaps == null -> "Checking what's already there…"
            gaps!!.isEmpty() -> "Everything in this range is already in Diafit."
            fetchDays != null && fetchDays < totalDays -> "Will fetch about $fetchDays of $totalDays days; the rest is already there."
            else -> "Will fetch $totalDays days."
        },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (needsHistory) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Health Connect only shares the last 30 days until you allow access to older data.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = onAllowHistory) { Text("Allow older data") }
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = { onBackfill(type, source, range) },
            enabled = !running && gaps?.isNotEmpty() == true && !needsHistory
        ) { Text("Fill in") }
        Spacer(Modifier.width(12.dp))
        when (mine) {
            is BackfillStatus.Running -> {
                LinearProgressIndicator(progress = { mine.progress }, modifier = Modifier.weight(1f))
            }
            is BackfillStatus.Done -> {
                Text(
                    if (mine.nothingMissing) "Nothing was missing." else "Added ${"%,d".format(mine.added)} from ${mine.connector.displayName}.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("OK") }
            }
            is BackfillStatus.Failed -> {
                Text(mine.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("OK") }
            }
            else -> if (running) Text("Another fill-in is running…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Preset day counts plus a custom first–last day, with the date range picker behind "Custom…". */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SpanChooser(span: BackfillSpan, onSpan: (BackfillSpan) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7, 14, 30, 90).forEach { d ->
            FilterChip(selected = span == BackfillSpan.LastDays(d), onClick = { onSpan(BackfillSpan.LastDays(d)) }, label = { Text("$d days") })
        }
        FilterChip(selected = span is BackfillSpan.Custom, onClick = { picking = true }, label = { Text(if (span is BackfillSpan.Custom) span.label() else "Custom…") })
    }
    if (picking) {
        val today = remember { LocalDate.now() }
        val pickerState = rememberDateRangePickerState(
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
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val start = day(pickerState.selectedStartDateMillis!!)
                        val end = pickerState.selectedEndDateMillis?.let(::day) ?: start
                        picking = false
                        onSpan(BackfillSpan.Custom(start, end))
                    }
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }
        ) {
            DateRangePicker(pickerState, Modifier.height(500.dp), showModeToggle = false)
        }
    }
}

/**
 * Fills in every data type a connector can hand over for one time range, in one go. The types default to the
 * ones this connector currently feeds; the user can tick others (data of an unselected type is stored but not shown).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConnectorBackfillPanel(
    connector: Connector,
    state: SettingsState,
    onStart: (Connector, List<DataType>, TimeRange) -> Unit,
    onDismiss: () -> Unit,
    missingRanges: suspend (DataType, TimeRange) -> List<TimeRange>,
    onAllowHistory: () -> Unit
) {
    val types = remember(connector) { DataType.values().filter { it in connector.canBackfill } }
    var open by rememberSaveable(connector.name) { mutableStateOf(false) }
    var chosen by remember(connector) {
        val fed = types.filter { state.selections[it] == connector }
        mutableStateOf((fed.ifEmpty { types }).map { it.name }.toSet())
    }
    val selected = types.filter { it.name in chosen }
    var span by remember { mutableStateOf<BackfillSpan>(BackfillSpan.LastDays(14)) }
    val range = remember(span) { span.toRange() }
    val status = state.backfill
    val running = status is BackfillStatus.Running || status is BackfillStatus.RunningAll
    val mine = (status as? BackfillStatus.RunningAll)?.takeIf { it.connector == connector }
        ?: (status as? BackfillStatus.DoneAll)?.takeIf { it.connector == connector }

    val gaps by produceState<Map<DataType, List<TimeRange>>?>(null, selected, range.start, range.end, status is BackfillStatus.DoneAll) {
        value = null
        value = selected.associateWith { missingRanges(it, range) }.filterValues { it.isNotEmpty() }
    }
    val needsHistory = connector == Connector.HEALTH_CONNECT && !state.healthConnect.historyGranted &&
        range.start < System.currentTimeMillis() - 29 * DAY_MS

    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = !open }.padding(vertical = 6.dp)
        ) {
            Text("Fill in past data", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(open) {
            Column {
                Text(
                    "Fetches the history of every kind of data below from ${connector.displayName}, for the time range you pick. What Diafit already has is skipped.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { type ->
                        FilterChip(
                            selected = type.name in chosen,
                            onClick = { chosen = if (type.name in chosen) chosen - type.name else chosen + type.name },
                            label = { Text(type.label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SpanChooser(span) { span = it }
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        selected.isEmpty() -> "Pick at least one kind of data."
                        gaps == null -> "Checking what's already there…"
                        gaps!!.isEmpty() -> "Everything in this range is already in Diafit."
                        else -> "Missing: ${gaps!!.keys.joinToString(", ") { it.label }}."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (needsHistory) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Health Connect only shares the last 30 days until you allow access to older data.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onAllowHistory) { Text("Allow older data") }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onStart(connector, selected, range) },
                        enabled = !running && gaps?.isNotEmpty() == true && !needsHistory
                    ) { Text("Fill in all") }
                    Spacer(Modifier.width(12.dp))
                    when (mine) {
                        is BackfillStatus.RunningAll -> Column(Modifier.weight(1f)) {
                            Text("${mine.type.label} (${mine.index + 1} of ${mine.count})", style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(progress = { mine.progress }, modifier = Modifier.fillMaxWidth())
                        }
                        is BackfillStatus.DoneAll -> {
                            Text(
                                if (mine.nothingMissing) "Nothing was missing."
                                else "Added " + mine.added.entries.joinToString(", ") { (t, n) -> "${"%,d".format(n)} ${t.label.lowercase()}" } + ".",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismiss) { Text("OK") }
                        }
                        else -> if (running) Text("Another fill-in is running…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                (mine as? BackfillStatus.DoneAll)?.failed?.takeIf { it.isNotEmpty() }?.let { failed ->
                    Spacer(Modifier.height(4.dp))
                    failed.forEach { (t, msg) ->
                        Text("${t.label} failed: $msg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
