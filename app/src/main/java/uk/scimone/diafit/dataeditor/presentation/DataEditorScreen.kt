package uk.scimone.diafit.dataeditor.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.core.domain.model.formatDurationMinutes
import uk.scimone.diafit.core.domain.util.friendlyDateString
import uk.scimone.diafit.dataeditor.data.EditableRecord
import uk.scimone.diafit.dataeditor.data.EditableRecord.*
import uk.scimone.diafit.dataeditor.data.RecordKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

internal fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(TimeFormat)

/**
 * Pick a record type and a day, then correct or remove any stored record of it: a glucose value, an insulin
 * dose, a meal's carbs, the length of a workout or sleep session, a device event. Changes are written straight
 * to the database; deleting offers an undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataEditorScreen(userId: Int, onBack: () -> Unit) {
    val vm: DataEditorViewModel = koinViewModel { parametersOf(userId) }
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<EditableRecord?>(null) }
    /** A blank record being added; shares the edit sheet. */
    var adding by remember { mutableStateOf<EditableRecord?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected = emptySet() }
    // A different list means a different selection.
    LaunchedEffect(state.kind, state.day) { selected = emptySet() }
    var pickDay by remember { mutableStateOf(false) }

    LaunchedEffect(state.undo) {
        val undo = state.undo
        if (undo.isEmpty()) return@LaunchedEffect
        val text = if (undo.size == 1) "${undo.first().kind.label} record deleted" else "${undo.size} records deleted"
        val result = snackbar.showSnackbar(text, actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.undoShown()
    }
    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(m)
        vm.messageShown()
    }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Cancel selection") } },
                    actions = {
                        IconButton(onClick = { selected = state.records.map { it.key }.toSet() }) { Icon(Icons.Filled.SelectAll, "Select all") }
                        IconButton(onClick = { confirmBulkDelete = true }) { Icon(Icons.Filled.Delete, "Delete selected", tint = MaterialTheme.colorScheme.error) }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Data editor") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = { adding = blankRecord(state.kind, state.day, userId) },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Add ${state.kind.single}") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RecordKind.entries.toList()) { kind ->
                    FilterChip(selected = kind == state.kind, onClick = { vm.selectKind(kind) }, label = { Text(kind.label) })
                }
            }
            DayNavigator(
                day = state.day,
                onPrev = { vm.shiftDay(-1) },
                onNext = { vm.shiftDay(1) },
                onPick = { pickDay = true }
            )
            HorizontalDivider()
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.records.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No ${state.kind.plural} on this day.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "${state.records.size} ${state.kind.plural} · tap to edit, hold to select several",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    items(state.records, key = { it.key }) { record ->
                        val isSelected = record.key in selected
                        RecordRow(
                            record, selecting = selecting, selected = isSelected,
                            onLongClick = { selected = selected + record.key },
                            onClick = {
                                if (selecting) selected = if (isSelected) selected - record.key else selected + record.key
                                else editing = record
                            }
                        )
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    // Room for the add button.
                    item { androidx.compose.foundation.layout.Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }

    editing?.let { record ->
        RecordEditSheet(
            record = record, isNew = false,
            onDismiss = { editing = null },
            onSave = { updated -> vm.save(updated) { ok -> if (ok) editing = null } },
            onDelete = { vm.delete(listOf(record)); editing = null }
        )
    }
    adding?.let { blank ->
        RecordEditSheet(
            record = blank, isNew = true,
            onDismiss = { adding = null },
            onSave = { created -> vm.create(created) { ok -> if (ok) adding = null } },
            onDelete = {}
        )
    }
    if (confirmBulkDelete) {
        val n = selected.size
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text("Delete $n ${if (n == 1) "record" else "records"}?") },
            text = { Text("They disappear from charts, statistics and patterns. You can undo right after.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBulkDelete = false
                    val doomed = state.records.filter { it.key in selected }
                    vm.delete(doomed) { selected = emptySet() }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmBulkDelete = false }) { Text("Cancel") } }
        )
    }

    if (pickDay) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = state.day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(LocalDate.now())
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickDay = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { vm.selectDay(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    pickDay = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDay = false }) { Text("Cancel") } }
        ) { DatePicker(picker, showModeToggle = false) }
    }
}

@Composable
private fun DayNavigator(day: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, "Previous day") }
        TextButton(onClick = onPick, modifier = Modifier.weight(1f)) {
            val ms = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val label = friendlyDateString(ms)
            val date = day.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
            Text(if (label == "Today" || label == "Yesterday") "$label · $date" else date, style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = onNext, enabled = day.isBefore(LocalDate.now())) { Icon(Icons.Filled.ChevronRight, "Next day") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordRow(record: EditableRecord, selecting: Boolean, selected: Boolean, onLongClick: () -> Unit, onClick: () -> Unit) {
    val (title, subtitle) = summary(record)
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = null)
            androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        }
        Text(
            clock(record.timeUtc), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(52.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!selecting) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Title and a secondary line for a list row. */
private fun summary(record: EditableRecord): Pair<String, String> = when (record) {
    is Glucose -> "${record.e.valueMgdl} mg/dL" to listOf(record.e.direction, record.e.source).filter { it.isNotBlank() && it != "Unknown" }.joinToString(" · ")
    is Insulin -> "${trim(record.e.value)} U" to listOf(if (record.e.isSmb) "Automatic (SMB)" else "Manual", record.e.eventType).filter { it.isNotBlank() }.joinToString(" · ")
    is Meal -> (record.e.description?.takeIf { it.isNotBlank() } ?: record.e.mealType.type) to
        "${record.e.carbohydrates} g carbs · ${record.e.proteins ?: 0} g protein · ${record.e.fats ?: 0} g fat"
    is Workout -> (record.e.title ?: "Exercise") to
        "${clock(record.e.startUtc)}–${clock(record.e.endUtc)} · ${formatDurationMinutes(((record.e.endUtc - record.e.startUtc) / 60_000).toInt())}"
    is Sleep -> "Slept ${formatDurationMinutes(((record.endUtc - record.startUtc) / 60_000).toInt())}" to
        "${clock(record.startUtc)} → ${clock(record.endUtc)}" + if (record.stages.size > 1) " · ${record.stages.size} stages" else ""
    is Device -> record.e.eventType to (record.e.notes ?: "")
    is HeartRate -> "${record.e.bpm} bpm" to ""
    is Steps -> "%,d steps".format(record.e.count) to "${clock(record.e.startUtc)}–${clock(record.e.startUtc + 15 * 60_000L)}"
}

internal fun trim(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(Locale.US, v).trimEnd('0').trimEnd('.')

