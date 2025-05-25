package uk.scimone.diafit.addmeal.presentation.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun MealDateTimePicker(
    initial: LocalDateTime?,
    onDateTimeSelected: (LocalDateTime) -> Unit
) {
    val context = LocalContext.current
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val current = initial ?: LocalDateTime.now()
    var displayText by remember { mutableStateOf(current.format(formatter)) }

    Button(onClick = {
        val now = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val selected = LocalDateTime.of(year, month + 1, day, hour, minute)
                        displayText = selected.format(formatter)
                        onDateTimeSelected(selected)
                    },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    true
                ).show()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        ).show()
    }) {
        Text("Pick Meal Time: $displayText")
    }
}
