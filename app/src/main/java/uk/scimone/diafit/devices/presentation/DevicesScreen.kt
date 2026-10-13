package uk.scimone.diafit.devices.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import uk.scimone.diafit.core.domain.model.DeviceAge
import uk.scimone.diafit.core.domain.model.DeviceStatus
import uk.scimone.diafit.R
import uk.scimone.diafit.core.presentation.duration
import uk.scimone.diafit.core.presentation.formatStamp
import uk.scimone.diafit.core.presentation.rememberResources
import androidx.compose.ui.res.stringResource
import uk.scimone.diafit.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(userId: Int, onBack: () -> Unit) {
    val viewModel: DevicesViewModel = koinViewModel { parametersOf(userId) }
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.ages, key = { it.kind.name }) { AgeCard(it) }
            item { StatusCard(state.status, state.nowUtc, state.omnipod) }
            item { LifetimesCard(userId) }
        }
    }
}

@Composable
private fun AgeCard(age: DeviceAge) {
    val resources = rememberResources()
    val remaining = age.remainingMs
    val overdue = remaining != null && remaining < 0
    val soon = remaining != null && remaining in 0..(12 * 3_600_000L)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(age.kind.labelRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(
                    when {
                        remaining == null -> stringResource(R.string.device_no_change)
                        overdue -> stringResource(R.string.devices_overdue_by, resources.duration(-remaining))
                        else -> stringResource(R.string.devices_due_in, resources.duration(remaining))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (overdue || soon) Warning else MaterialTheme.colorScheme.primary
                )
            }
            val used = age.fractionUsed
            if (used != null) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { used.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (overdue || soon) Warning else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.devices_age_line, stamp(age.changedAtUtc!!), resources.duration(age.ageMs!!), stamp(age.expiresAtUtc!!)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Collapsible: how long each consumable lasts, which the expiry estimates and alerts are based on. */
@Composable
private fun LifetimesCard(userId: Int) {
    var open by rememberSaveable { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.devices_lifetimes_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.devices_lifetimes_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, stringResource(if (open) R.string.common_hide else R.string.common_edit))
            }
            if (open) {
                Spacer(Modifier.height(12.dp))
                DeviceLifetimeSettings(userId)
            }
        }
    }
}

@Composable
private fun StatusCard(status: DeviceStatus?, now: Long, omnipod: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.devices_levels), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (status == null) {
                Text(
                    stringResource(R.string.devices_levels_empty),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val resources = rememberResources()
                status.reservoirUnits?.let { LevelRow(stringResource(R.string.devices_reservoir), if (omnipod && it >= 50) "50+ U" else "%.0f U".format(it)) }
                status.pumpBatteryPercent?.let { LevelRow(stringResource(R.string.devices_pump_battery), "%.0f %%".format(it)) }
                status.pumpBatteryVolt?.let { LevelRow(stringResource(R.string.devices_pump_battery), "%.2f V".format(it)) }
                status.uploaderBatteryPercent?.let { LevelRow(stringResource(R.string.devices_phone_battery), "%.0f %%".format(it)) }
                status.pumpStatus?.lineSequence()?.firstOrNull()?.let { LevelRow(stringResource(R.string.devices_pump_status), it) }
                if (!status.hasPump && status.uploaderBatteryPercent == null)
                    Text(stringResource(R.string.devices_no_levels), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.devices_updated, resources.duration(now - status.timestampUtc)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LevelRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun stamp(utc: Long) = formatStamp(utc, "dMMMHm")
