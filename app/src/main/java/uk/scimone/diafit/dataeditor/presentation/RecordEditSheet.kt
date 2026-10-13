package uk.scimone.diafit.dataeditor.presentation

import uk.scimone.diafit.core.presentation.formatStamp
import uk.scimone.diafit.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.formatDurationMinutes
import uk.scimone.diafit.dataeditor.data.DataEditorRepository
import uk.scimone.diafit.dataeditor.data.EditableRecord
import uk.scimone.diafit.dataeditor.data.EditableRecord.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

private fun Long.toLocal(): LocalDateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()
private fun LocalDateTime.toMs(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Keeps the stored millisecond value unless the user actually moved the time (pickers only know minutes). */
private fun resolve(originalMs: Long, picked: LocalDateTime): Long =
    if (picked == originalMs.toLocal().truncatedTo(ChronoUnit.MINUTES)) originalMs else picked.toMs()

/** Bottom sheet that edits one record. [onSave] gets the changed copy; [onDelete] removes the record. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditSheet(record: EditableRecord, isNew: Boolean, onDismiss: () -> Unit, onSave: (EditableRecord) -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(if (isNew) R.string.de_new_record else R.string.de_edit_record, stringResource(record.kind.labelRes)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            when (record) {
                is Glucose -> GlucoseForm(record, isNew, onSave)
                is Insulin -> InsulinForm(record, isNew, onSave)
                is Meal -> MealForm(record, isNew, onSave)
                is Workout -> WorkoutForm(record, isNew, onSave)
                is Sleep -> SleepForm(record, isNew, onSave)
                is Device -> DeviceForm(record, isNew, onSave)
                is HeartRate -> HeartRateForm(record, isNew, onSave)
                is Steps -> StepsForm(record, isNew, onSave)
            }
            if (!isNew) importNote(record)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isNew) OutlinedButton(
                onClick = { confirmDelete = true },
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.de_delete_this))
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.de_delete_this_q)) },
            text = { Text(stringResource(R.string.de_delete_text)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun importNote(record: EditableRecord): String? {
    val source = when (record) {
        is Glucose -> record.e.source.takeIf { it != "Unknown" && it.isNotBlank() }
        is Insulin -> if (record.e.sourceId != null) stringResource(R.string.de_src_pump) else null
        is Meal -> if (record.e.sourceId != null) stringResource(R.string.de_src_aaps_ns) else null
        is Workout, is Sleep, is HeartRate, is Steps -> "Health Connect"
        is Device -> stringResource(R.string.de_src_aaps_ns)
    } ?: return null
    return stringResource(R.string.de_imported, source)
}

private val DEVICE_TYPES = listOf("Site Change", "Insulin Change", "Sensor Start", "Sensor Change", "Pump Battery Change", "Note", "Announcement")

// ---------------------------------------------------------------- forms

@Composable
private fun SaveButton(enabled: Boolean, isNew: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(if (isNew) stringResource(R.string.de_add) else stringResource(R.string.de_save_changes)) }
}

@Composable
private fun GlucoseForm(r: Glucose, isNew: Boolean, onSave: (EditableRecord) -> Unit) {
    var time by remember { mutableStateOf(r.e.timestamp.toLocal().truncatedTo(ChronoUnit.MINUTES)) }
    var value by remember { mutableStateOf(if (isNew) "" else r.e.valueMgdl.toString()) }
    val v = value.toIntOrNull()
    val valid = v != null && v in 20..700
    DateTimeField(stringResource(R.string.de_time), time) { time = it }
    NumberField(stringResource(R.string.de_kind_glucose), value, "mg/dL", { value = it }, error = value.isNotEmpty() && !valid, hint = "20–700")
    SaveButton(valid, isNew) { onSave(Glucose(r.e.copy(timestamp = resolve(r.e.timestamp, time), valueMgdl = v!!))) }
}

@Composable
private fun InsulinForm(r: Insulin, isNew: Boolean, onSave: (EditableRecord) -> Unit) {
    var time by remember { mutableStateOf(r.e.timestampUtc.toLocal().truncatedTo(ChronoUnit.MINUTES)) }
    var value by remember { mutableStateOf(if (isNew) "" else trim(r.e.value)) }
    var smb by remember { mutableStateOf(r.e.isSmb) }
    val v = value.replace(',', '.').toFloatOrNull()
    val valid = v != null && v > 0f && v <= 100f
    DateTimeField(stringResource(R.string.de_time), time) { time = it }
    NumberField(stringResource(R.string.de_dose), value, "U", { value = it }, decimal = true, error = value.isNotEmpty() && !valid, hint = stringResource(R.string.de_dose_hint))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.de_smb), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.de_smb_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = smb, onCheckedChange = { smb = it })
    }
    SaveButton(valid, isNew) {
        onSave(Insulin(r.e.copy(timestampUtc = resolve(r.e.timestampUtc, time), value = v!!, isSmb = smb, updatedAtUtc = System.currentTimeMillis())))
    }
}

@Composable
private fun MealForm(r: Meal, isNew: Boolean, onSave: (EditableRecord) -> Unit) {
    var time by remember { mutableStateOf(r.e.mealTimeUtc.toLocal().truncatedTo(ChronoUnit.MINUTES)) }
    var name by remember { mutableStateOf(r.e.description.orEmpty()) }
    var carbs by remember { mutableStateOf(if (isNew) "" else r.e.carbohydrates.toString()) }
    var protein by remember { mutableStateOf((r.e.proteins ?: 0).toString()) }
    var fat by remember { mutableStateOf((r.e.fats ?: 0).toString()) }
    var kcal by remember { mutableStateOf((r.e.calories ?: 0).toString()) }
    val c = carbs.toIntOrNull(); val p = protein.toIntOrNull(); val f = fat.toIntOrNull(); val k = kcal.toIntOrNull()
    val valid = c != null && p != null && f != null && k != null && listOf(c, p, f, k).all { it in 0..5000 }
    DateTimeField(stringResource(R.string.de_time), time) { time = it }
    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.de_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberField(stringResource(R.string.macro_carbs), carbs, "g", { carbs = it }, Modifier.weight(1f), error = c == null)
        NumberField(stringResource(R.string.macro_protein), protein, "g", { protein = it }, Modifier.weight(1f), error = p == null)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberField(stringResource(R.string.macro_fat), fat, "g", { fat = it }, Modifier.weight(1f), error = f == null)
        NumberField(stringResource(R.string.macro_energy), kcal, "kcal", { kcal = it }, Modifier.weight(1f), error = k == null)
    }
    if (r.e.components.isNotEmpty()) {
        Text(
            stringResource(R.string.de_meal_split_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    SaveButton(valid, isNew) {
        val at = resolve(r.e.mealTimeUtc, time)
        val base = r.e.copy(mealTimeUtc = at, description = name.trim().ifBlank { null }, carbohydrates = c!!, proteins = p!!, fats = f!!, calories = k!!)
        onSave(Meal(
            if (isNew) base.copy(
                mealType = uk.scimone.diafit.core.domain.model.MealEntity.suggestMealType(at.toLocal().hour, c),
                impactType = uk.scimone.diafit.core.domain.model.MealEntity.inferImpactType(c, p, f)
            ) else base
        ))
    }
}

@Composable
private fun WorkoutForm(r: Workout, isNew: Boolean, onSave: (EditableRecord) -> Unit) {
    var start by remember { mutableStateOf(r.e.startUtc.toLocal().truncatedTo(ChronoUnit.MINUTES)) }
    var title by remember { mutableStateOf(r.e.title.orEmpty()) }
    var minutes by remember { mutableStateOf(if (isNew) "" else ((r.e.endUtc - r.e.startUtc) / 60_000).toString()) }
    val m = minutes.toLongOrNull()
    val valid = m != null && m in 1..1440
    DateTimeField(stringResource(R.string.de_start), start) { start = it }
    NumberField(stringResource(R.string.de_duration), minutes, "min", { minutes = it }, error = minutes.isNotEmpty() && !valid, hint = "1–1440")
    if (valid) Text(stringResource(R.string.de_ends_at, clock(resolve(r.e.startUtc, start) + m!! * 60_000), formatDurationMinutes(m.toInt())), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.de_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    SaveButton(valid, isNew) {
        val s = resolve(r.e.startUtc, start)
        onSave(Workout(r.e.copy(startUtc = s, endUtc = s + m!! * 60_000, title = title.trim().ifBlank { null })))
    }
}

@Composable
private fun SleepForm(r: Sleep, isNew: Boolean, onSave: (EditableRecord) -> Unit) {
    var start by remember { mutableStateOf(r.startUtc.toLocal().truncatedTo(ChronoUnit.MINUTES)) }
    var end by remember { mutableStateOf(r.endUtc.toLocal().truncatedTo(ChronoUnit.MINUTES)) }
    val valid = end.isAfter(start) && ChronoUnit.HOURS.between(start, end) <= 24
    DateTimeField(stringResource(R.string.de_fell_asleep), start) { start = it }
    DateTimeField(stringResource(R.string.de_woke_up), end) { end = it }
    if (valid) Text(stringResource(R.string.de_slept_total, formatDurationMinutes(ChronoUnit.MINUTES.between(start, end).toInt())), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    else Text(stringResource(R.string.de_sleep_invalid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    if (r.stages.size > 1) {
        Text(stringResource(R.string.de_sleep_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SaveButton(valid, isNew) {
        val s = resolve(r.startUtc, start); val e = resolve(r.endUtc, end)
        onSave(Sleep(DataEditorRepository.resizeSleep(r.stages, s, e).ifEmpty { listOf(r.stages.first().copy(startUtc = s, endUtc = e, sessionStartUtc = s, sessionEndUtc = e)) }))
    }
}

@Composable
private fun DeviceForm(r: Device, isNew: Boolean, onSave: (EditableRecord) -> Unit) {
    var time by remember { mutableStateOf(r.e.timestampUtc.toLocal().truncatedTo(ChronoUnit.MINUTES)) }
    var notes by remember { mutableStateOf(r.e.notes.orEmpty()) }
    var duration by remember { mutableStateOf(r.e.durationMinutes?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }.orEmpty()) }
    val d = duration.replace(',', '.').toDoubleOrNull()
    val valid = duration.isBlank() || (d != null && d >= 0)
    var type by remember { mutableStateOf(r.e.eventType) }
    if (isNew) {
        Text(stringResource(R.string.de_type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DEVICE_TYPES.forEach { t -> androidx.compose.material3.FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) }) }
        }
    } else Text(r.e.eventType, style = MaterialTheme.typography.titleMedium)
    DateTimeField(stringResource(R.string.de_time), time) { time = it }
    OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.de_notes)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    NumberField(stringResource(R.string.de_duration_optional), duration, "min", { duration = it }, decimal = true, error = !valid)
    SaveButton(valid, isNew) {
        onSave(Device(r.e.copy(eventType = type, timestampUtc = resolve(r.e.timestampUtc, time), notes = notes.trim().ifBlank { null }, durationMinutes = d)))
    }
}

@Composable
private fun HeartRateForm(r: HeartRate, isNew: Boolean, onSave: (EditableRecord) -> Unit) {
    var time by remember { mutableStateOf(r.e.timestamp.toLocal().truncatedTo(ChronoUnit.MINUTES)) }
    var bpm by remember { mutableStateOf(if (isNew) "" else r.e.bpm.toString()) }
    val v = bpm.toIntOrNull()
    val valid = v != null && v in 20..260
    DateTimeField(stringResource(R.string.de_time), time) { time = it }
    NumberField(stringResource(R.string.datatype_heart_rate), bpm, "bpm", { bpm = it }, error = bpm.isNotEmpty() && !valid, hint = "20–260")
    SaveButton(valid, isNew) { onSave(HeartRate(r.e.copy(timestamp = resolve(r.e.timestamp, time), bpm = v!!))) }
}

@Composable
private fun StepsForm(r: Steps, isNew: Boolean, onSave: (EditableRecord) -> Unit) {
    var count by remember { mutableStateOf(if (isNew) "" else r.e.count.toString()) }
    val v = count.toIntOrNull()
    val valid = v != null && v in 0..20_000
    var time by remember { mutableStateOf(r.e.startUtc.toLocal().truncatedTo(ChronoUnit.MINUTES)) }
    if (isNew) {
        DateTimeField(stringResource(R.string.de_slot_start), time) { time = it }
        Text(stringResource(R.string.de_steps_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(stringResource(R.string.de_slot_line, clock(r.e.startUtc), clock(r.e.startUtc + 15 * 60_000L)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    NumberField(stringResource(R.string.datatype_steps), count, "", { count = it }, error = count.isNotEmpty() && !valid, hint = "0–20,000")
    SaveButton(valid, isNew) {
        val slot = uk.scimone.diafit.core.domain.model.StepsEntity.STEP_BUCKET_MS
        val start = if (isNew) time.toMs().let { it - Math.floorMod(it, slot) } else r.e.startUtc
        onSave(Steps(r.e.copy(startUtc = start, count = v!!)))
    }
}

// ---------------------------------------------------------------- fields

@Composable
private fun NumberField(
    label: String, value: String, unit: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    decimal: Boolean = false, error: Boolean = false, hint: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || (decimal && (c == '.' || c == ',')) }) },
        label = { Text(label) },
        suffix = if (unit.isNotEmpty()) { { Text(unit) } } else null,
        supportingText = if (error && hint != null) { { Text(hint) } } else null,
        isError = error,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.fillMaxWidth()
    )
}

/** Date and time as two pill buttons that open the Material pickers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(label: String, value: LocalDateTime, onChange: (LocalDateTime) -> Unit) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(formatStamp(value.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), "EEEdMMMy"), true) { pickDate = true }
            Pill(java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(value), false) { pickTime = true }
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(LocalDate.now())
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(value.with(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())) }
                    pickDate = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.common_cancel)) } }
        ) { DatePicker(state, showModeToggle = false) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(label) },
            text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { TimePicker(state) } },
            confirmButton = { TextButton(onClick = { onChange(value.withHour(state.hour).withMinute(state.minute)); pickTime = false }) { Text(stringResource(R.string.common_ok)) } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun Pill(text: String, calendar: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.height(40.dp)) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (calendar) Icons.Outlined.CalendarToday else Icons.Outlined.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
