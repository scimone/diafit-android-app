package uk.scimone.diafit.devices.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import uk.scimone.diafit.core.data.nightscout.DeviceLifetimeStore
import uk.scimone.diafit.core.domain.model.DeviceKind
import uk.scimone.diafit.core.domain.model.hours

/** Days + hours fields per consumable; the Devices page estimates expiry as last change + this. */
@Composable
fun DeviceLifetimeSettings() {
    val store: DeviceLifetimeStore = koinInject()
    val lifetimes by store.lifetimes.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "How long each lasts before it should be changed (Omnipod pods: about 3 days 8 hours). Used to estimate expiry on the Devices page.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DeviceKind.configurable.forEach { kind ->
            val total = lifetimes.hours(kind)
            var days by remember(kind) { mutableStateOf((total / 24).toString()) }
            var hours by remember(kind) { mutableStateOf((total % 24).toString()) }
            fun save() {
                val t = (days.toIntOrNull() ?: 0) * 24 + (hours.toIntOrNull() ?: 0)
                if (t > 0) store.set(kind, t)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(kind.label, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = days, onValueChange = { days = it.filter(Char::isDigit).take(3); save() },
                    singleLine = true, suffix = { Text("d") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(88.dp)
                )
                OutlinedTextField(
                    value = hours, onValueChange = { hours = it.filter(Char::isDigit).take(2); save() },
                    singleLine = true, suffix = { Text("h") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(88.dp)
                )
            }
        }
    }
}
