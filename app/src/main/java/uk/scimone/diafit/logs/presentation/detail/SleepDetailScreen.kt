package uk.scimone.diafit.logs.presentation.detail

import uk.scimone.diafit.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.SleepStage
import uk.scimone.diafit.logs.presentation.components.formatDuration
import uk.scimone.diafit.logs.presentation.components.stageColor
import uk.scimone.diafit.logs.presentation.model.SleepEntryUi
import uk.scimone.diafit.ui.theme.Sleep
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One night's sleep: when, how long, and the split into stages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepDetailScreen(entry: SleepEntryUi, onBack: () -> Unit) {
    val clock = uk.scimone.diafit.core.presentation.rememberLocalizedFormat("Hm")
    val date = uk.scimone.diafit.core.presentation.localizedFormat("EEEEdMMMM")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.datatype_sleep)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(date.format(Date(entry.endUtc)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${clock.format(Date(entry.startUtc))} – ${clock.format(Date(entry.endUtc))}", style = MaterialTheme.typography.titleMedium)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatDuration(entry.asleepMs), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Sleep)
                Text(" " + stringResource(R.string.logs_asleep), style = MaterialTheme.typography.titleMedium, color = Sleep, modifier = Modifier.padding(bottom = 6.dp))
            }
            if (!entry.hasStages) {
                Text(stringResource(R.string.logs_sleep_no_stages), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val ms = { st: SleepStage -> entry.stageMs[st] ?: 0L }
                uk.scimone.diafit.stats.presentation.SleepStages(
                    uk.scimone.diafit.stats.domain.SleepNight(
                        deepMs = ms(SleepStage.DEEP), remMs = ms(SleepStage.REM),
                        lightMs = ms(SleepStage.LIGHT) + ms(SleepStage.SLEEPING), awakeMs = ms(SleepStage.AWAKE),
                        bedMinutes = 0, wakeMinutes = 0, startUtc = entry.startUtc, endUtc = entry.endUtc, spans = entry.spans
                    )
                )
            }
        }
    }
}
