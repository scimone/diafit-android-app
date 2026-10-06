package uk.scimone.diafit.addmeal.presentation.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.util.friendlyDateString
import uk.scimone.diafit.core.domain.util.localDateTimeToInstant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Date chip + time chip (each opens its own picker, keeping the other half) and a "Now" shortcut. */
@Composable
fun MealDateTimePicker(
    value: LocalDateTime?,
    onValueChange: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val current = value ?: LocalDateTime.now()

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        AssistChip(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, y, m, d -> onValueChange(current.withYear(y).withMonth(m + 1).withDayOfMonth(d)) },
                    current.year, current.monthValue - 1, current.dayOfMonth
                ).show()
            },
            label = { Text(friendlyDateString(localDateTimeToInstant(current).toEpochMilli())) },
            leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(18.dp)) }
        )
        AssistChip(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, h, min -> onValueChange(current.withHour(h).withMinute(min)) },
                    current.hour, current.minute, true
                ).show()
            },
            label = { Text(current.format(DateTimeFormatter.ofPattern("HH:mm"))) },
            leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) }
        )
        TextButton(onClick = { onValueChange(LocalDateTime.now()) }) { Text("Now") }
    }
}
