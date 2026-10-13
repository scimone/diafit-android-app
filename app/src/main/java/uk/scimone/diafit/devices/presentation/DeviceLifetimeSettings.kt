package uk.scimone.diafit.devices.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.koin.compose.koinInject
import uk.scimone.diafit.R
import uk.scimone.diafit.core.data.nightscout.DeviceLifetimeStore
import uk.scimone.diafit.core.data.nightscout.DeviceStatusStore
import uk.scimone.diafit.core.data.nightscout.DeviceWarnStore
import uk.scimone.diafit.core.domain.model.WarnLevels
import uk.scimone.diafit.core.domain.model.DeviceKind
import uk.scimone.diafit.core.domain.model.DeviceLayout
import uk.scimone.diafit.core.domain.model.deviceLayout
import uk.scimone.diafit.core.domain.model.hours

/**
 * Days + hours fields per consumable; the Devices page estimates expiry as last change + this. Shows the same
 * rows as the Devices page: one "patch pump" row instead of site + insulin, and no battery without battery data.
 */
@Composable
fun DeviceLifetimeSettings(userId: Int = 1) {
    val store: DeviceLifetimeStore = koinInject()
    val inputsSource: DeviceInputsSource = koinInject()
    val statusStore: DeviceStatusStore = koinInject()
    val warnStore: DeviceWarnStore = koinInject()
    val warn by warnStore.thresholds.collectAsState()
    val lifetimes by store.lifetimes.collectAsState()
    val status by statusStore.status.collectAsState()
    var layout by remember { mutableStateOf(DeviceLayout(patch = false, showBattery = false)) }
    LaunchedEffect(status) {
        val now = System.currentTimeMillis()
        val inputs = inputsSource.load(userId, now)
        layout = deviceLayout(
            inputs.events,
            batteryReported = status?.let { it.pumpBatteryPercent != null || it.pumpBatteryVolt != null } == true,
            pumpType = inputs.pumpType
        )
    }
    val rows = buildList {
        add(DeviceKind.SENSOR)
        if (layout.patch) add(DeviceKind.PATCH) else { add(DeviceKind.SITE); add(DeviceKind.INSULIN) }
        if (layout.showBattery) add(DeviceKind.BATTERY)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.devices_lifetimes_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        rows.forEach { kind ->
            val total = lifetimes.hours(if (kind == DeviceKind.PATCH) DeviceKind.SITE else kind)
            var days by remember(kind, total) { mutableStateOf((total / 24).toString()) }
            var hours by remember(kind, total) { mutableStateOf((total % 24).toString()) }
            fun save() {
                val t = (days.toIntOrNull() ?: 0) * 24 + (hours.toIntOrNull() ?: 0)
                if (t <= 0) return
                if (kind == DeviceKind.PATCH) { store.set(DeviceKind.SITE, t); store.set(DeviceKind.INSULIN, t) } else store.set(kind, t)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(kind.labelRes), modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = days, onValueChange = { days = it.filter(Char::isDigit).take(3); save() },
                    singleLine = true, suffix = { Text(stringResource(R.string.unit_day_suffix)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(88.dp)
                )
                OutlinedTextField(
                    value = hours, onValueChange = { hours = it.filter(Char::isDigit).take(2); save() },
                    singleLine = true, suffix = { Text(stringResource(R.string.unit_hour_suffix)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(88.dp)
                )
            }
        }
        Text(
            stringResource(R.string.devices_warn_title), style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            stringResource(R.string.devices_warn_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val warnKinds = buildList {
            add(DeviceKind.SENSOR)
            add(if (layout.patch) DeviceKind.PATCH else DeviceKind.SITE)
            add(DeviceKind.INSULIN)
            if (layout.showBattery) add(DeviceKind.BATTERY)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.devices_warn_initial), Modifier.width(88.dp), color = Color(0xFFE09A00), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.devices_warn_urgent), Modifier.width(88.dp), color = Color(0xFFE53935), style = MaterialTheme.typography.labelMedium)
        }
        warnKinds.forEach { kind ->
            val lv = warn.of(kind)
            var initial by remember(kind, lv) { mutableStateOf(lv.initial.toString()) }
            var urgent by remember(kind, lv) { mutableStateOf(lv.urgent.toString()) }
            val unit = stringResource(if (kind == DeviceKind.INSULIN) R.string.unit_insulin_suffix else R.string.unit_hour_suffix)
            fun save() {
                val i = initial.toIntOrNull() ?: return
                val u = urgent.toIntOrNull() ?: return
                if (i > 0 && u in 0..i) warnStore.set(kind, WarnLevels(i, u))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(kind.labelRes), modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = initial, onValueChange = { initial = it.filter(Char::isDigit).take(4); save() },
                    singleLine = true, suffix = { Text(unit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(88.dp)
                )
                OutlinedTextField(
                    value = urgent, onValueChange = { urgent = it.filter(Char::isDigit).take(4); save() },
                    singleLine = true, suffix = { Text(unit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(88.dp)
                )
            }
        }
    }
}
