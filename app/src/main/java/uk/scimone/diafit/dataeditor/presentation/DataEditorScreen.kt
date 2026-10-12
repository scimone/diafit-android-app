package uk.scimone.diafit.dataeditor.presentation

import androidx.compose.foundation.clickable
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
    var pickDay by remember { mutableStateOf(false) }

    LaunchedEffect(state.undo) {
        val undo = state.undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("${undo.kind.label} record deleted", actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.undoShown()
    }
    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(m)
        vm.messageShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data editor") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
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
                            "${state.records.size} ${state.kind.plural} · tap one to correct or delete it",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    items(state.records, key = { it.key }) { record ->
                        RecordRow(record) { editing = record }
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    editing?.let { record ->
        RecordEditSheet(
            record = record,
            onDismiss = { editing = null },
            onSave = { updated -> vm.save(updated) { ok -> if (ok) editing = null } },
            onDelete = { vm.delete(record); editing = null }
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

@Composable
private fun RecordRow(record: EditableRecord, onClick: () -> Unit) {
    val (title, subtitle) = summary(record)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

