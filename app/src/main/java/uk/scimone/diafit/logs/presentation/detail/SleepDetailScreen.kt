package uk.scimone.diafit.logs.presentation.detail

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
    val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
    val date = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
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
                Text(" asleep", style = MaterialTheme.typography.titleMedium, color = Sleep, modifier = Modifier.padding(bottom = 6.dp))
            }
            if (!entry.hasStages) {
                Text("No sleep stages were recorded for this night.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val stages = listOf(SleepStage.DEEP, SleepStage.LIGHT, SleepStage.REM, SleepStage.AWAKE).filter { (entry.stageMs[it] ?: 0L) > 0L }
                val total = stages.sumOf { entry.stageMs[it] ?: 0L }.coerceAtLeast(1L)
                // Deep first, awake last: the shape of a good night at a glance.
                Row(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(10.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    stages.forEach { stage ->
                        Box(Modifier.weight((entry.stageMs[stage] ?: 0L) / total.toFloat()).fillMaxHeight().background(stage.stageColor()))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    stages.forEach { stage ->
                        val ms = entry.stageMs[stage] ?: 0L
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(stage.stageColor(), CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(stage.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(formatDuration(ms), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "  ${Math.round(ms * 100f / total)}%",
                                Modifier.width(52.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
