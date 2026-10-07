package uk.scimone.diafit.addmeal.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.core.domain.util.friendlyDateString
import uk.scimone.diafit.journal.presentation.model.accent
import uk.scimone.diafit.journal.presentation.model.iconRes
import uk.scimone.diafit.core.domain.util.localDateTimeToInstant
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The general info of an entry as one compact row of pills under the title: date, time and (for whole
 * meals) the meal type. Each opens a Material 3 picker or menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhenRow(
    value: LocalDateTime?,
    onValueChange: (LocalDateTime) -> Unit,
    mealType: MealType?,
    onMealTypeChange: (MealType) -> Unit,
    modifier: Modifier = Modifier
) {
    val current = value ?: LocalDateTime.now()
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickType by remember { mutableStateOf(false) }

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill(friendlyDateString(localDateTimeToInstant(current).toEpochMilli()), { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }) { pickDate = true }
        Pill(current.format(DateTimeFormatter.ofPattern("HH:mm")), { Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }) { pickTime = true }
        if (mealType != null) {
            Box {
                Pill(
                    mealType.type,
                    { Icon(painterResource(mealType.iconRes), null, Modifier.size(16.dp), tint = mealType.accent) },
                    trailing = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) }
                ) { pickType = true }
                DropdownMenu(expanded = pickType, onDismissRequest = { pickType = false }) {
                    MealType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.type, fontWeight = if (type == mealType) FontWeight.SemiBold else null) },
                            leadingIcon = { Icon(painterResource(type.iconRes), null, Modifier.size(20.dp), tint = type.accent) },
                            trailingIcon = if (type == mealType) { { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) } } else null,
                            onClick = { onMealTypeChange(type); pickType = false }
                        )
                    }
                }
            }
        }
    }

    if (pickDate) {
        // The picker works in UTC midnights; convert the local date both ways.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(LocalDate.now())
            }
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        onValueChange(current.with(d))
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } }
        ) {
            DatePicker(state, showModeToggle = false)
        }
    }

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Meal time") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) }
            },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(current.withHour(state.hour).withMinute(state.minute))
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Pill(text: String, leading: @Composable () -> Unit, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.height(36.dp)) {
        Row(Modifier.padding(start = 12.dp, end = if (trailing != null) 6.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            trailing?.invoke()
        }
    }
}
