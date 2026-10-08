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
import uk.scimone.diafit.ui.theme.Warning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(userId: Int, onBack: () -> Unit) {
    val viewModel: DevicesViewModel = koinViewModel { parametersOf(userId) }
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devices") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.ages, key = { it.kind.name }) { AgeCard(it) }
            item { StatusCard(state.status, state.nowUtc) }
            item {
                Text(
                    "Neither Nightscout nor AAPS reports an expiry date. It is estimated as the last change logged in the " +
                        "Journal plus the lifetime set in Settings.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AgeCard(age: DeviceAge) {
    val remaining = age.remainingMs
    val overdue = remaining != null && remaining < 0
    val soon = remaining != null && remaining in 0..(12 * 3_600_000L)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(age.kind.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(
                    when {
                        remaining == null -> "No change logged"
                        overdue -> "Overdue by ${duration(-remaining)}"
                        else -> "Due in ${duration(remaining)}"
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
                    "Changed ${stamp(age.changedAtUtc!!)} · ${duration(age.ageMs!!)} ago · expires ${stamp(age.expiresAtUtc!!)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusCard(status: DeviceStatus?, now: Long) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Levels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (status == null) {
                Text(
                    "Nothing yet. Connect Nightscout and select it for \"Pump & sensor status\" in Settings.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                status.reservoirUnits?.let { LevelRow("Reservoir", "%.0f U".format(it)) }
                status.pumpBatteryPercent?.let { LevelRow("Pump battery", "%.0f %%".format(it)) }
                status.pumpBatteryVolt?.let { LevelRow("Pump battery", "%.2f V".format(it)) }
                status.uploaderBatteryPercent?.let { LevelRow("Phone (uploader) battery", "%.0f %%".format(it)) }
                status.pumpStatus?.lineSequence()?.firstOrNull()?.let { LevelRow("Pump status", it) }
                if (!status.hasPump && status.uploaderBatteryPercent == null)
                    Text("Nightscout reports no pump or battery levels.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Updated ${duration(now - status.timestampUtc)} ago", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun duration(ms: Long): String {
    val minutes = abs(ms) / 60_000
    val days = minutes / 1440
    val hours = (minutes % 1440) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}

private fun stamp(utc: Long) = SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(utc))
