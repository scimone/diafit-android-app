package uk.scimone.diafit.backendsync.presentation

import uk.scimone.diafit.core.presentation.formatStamp
import androidx.compose.ui.res.stringResource
import uk.scimone.diafit.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import uk.scimone.diafit.settings.presentation.ServerConfigInput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Content of the Settings "Backend sync" section: address + token, connection check, last sync, sync now. */
@Composable
fun BackendSyncSettings(viewModel: BackendSyncViewModel = koinViewModel()) {
    val config by viewModel.config.collectAsState()
    val status by viewModel.status.collectAsState()
    val check by viewModel.check.collectAsState()
    // null = follow the saved config (form open only while nothing is set up); true while the user edits.
    var editing by remember { mutableStateOf<Boolean?>(null) }
    val open = editing ?: !config.isConfigured
    LaunchedEffect(check) { if (check is BackendCheck.Ok) editing = false }

    Text(
        stringResource(R.string.backend_intro),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))

    if (open) {
        var url by remember(config) { mutableStateOf(config.baseUrl) }
        var token by remember(config) { mutableStateOf(config.token) }
        ServerConfigInput(
            baseUrl = config.baseUrl,
            apiKey = config.token,
            baseUrlLabel = stringResource(R.string.backend_address),
            apiKeyLabel = stringResource(R.string.backend_token),
            onConfigChanged = { u, t -> url = u; token = t }
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { viewModel.saveAndTest(url, token) }, enabled = check != BackendCheck.Checking) {
                Text(stringResource(R.string.backend_save_test))
            }
            if (config.isConfigured) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        }
        CheckLine(check)
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            config.baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/'),
            style = MaterialTheme.typography.bodyMedium, maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        when (check) {
            is BackendCheck.Ok -> Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.settings_connected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            BackendCheck.Checking -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else -> TextButton(onClick = viewModel::test) { Text(stringResource(R.string.common_test)) }
        }
        TextButton(onClick = { editing = true }) { Text(stringResource(R.string.common_edit)) }
    }
    CheckLine(check)
    Spacer(Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val last = status.lastSyncAt
        Text(
            when {
                status.running -> stringResource(R.string.backend_syncing)
                last == null -> stringResource(R.string.backend_never)
                else -> stringResource(R.string.backend_last, formatTime(last))
            },
            style = MaterialTheme.typography.bodySmall
        )
        status.message?.let {
            Text(
                it, style = MaterialTheme.typography.bodySmall,
                color = if (status.lastOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = viewModel::syncNow, enabled = !status.running) { Text(stringResource(R.string.common_sync_now)) }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = viewModel::disconnect) { Text(stringResource(R.string.common_disconnect)) }
    }
}

@Composable
private fun CheckLine(check: BackendCheck) {
    when (check) {
        is BackendCheck.Ok -> Text(stringResource(R.string.backend_connected_as, check.username), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        is BackendCheck.Failed -> Text(check.message.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        BackendCheck.Checking -> Text(stringResource(R.string.common_checking), style = MaterialTheme.typography.bodySmall)
        BackendCheck.Idle -> Unit
    }
}

private fun formatTime(time: Long): String {
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).let { it.format(Date(time)) == it.format(Date()) }
    return formatStamp(time, if (sameDay) "Hm" else "dMMMHm")
}
