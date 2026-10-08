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

/** One hours field per consumable; the Devices page estimates expiry as last change + this. */
@Composable
fun DeviceLifetimeSettings() {
    val store: DeviceLifetimeStore = koinInject()
    val lifetimes by store.lifetimes.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "How long each lasts before it should be changed (Omnipod pods: about 80 hours). Used to estimate expiry on the Devices page.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DeviceKind.configurable.forEach { kind ->
            var text by remember(kind) { mutableStateOf(lifetimes.hours(kind).toString()) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(kind.label, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        text = v.filter { it.isDigit() }.take(5)
                        text.toIntOrNull()?.takeIf { it > 0 }?.let { store.set(kind, it) }
                    },
                    singleLine = true,
                    suffix = { Text("hours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp)
                )
            }
        }
    }
}
