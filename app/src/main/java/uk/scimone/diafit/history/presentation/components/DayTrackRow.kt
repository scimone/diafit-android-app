package uk.scimone.diafit.history.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.history.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.domain.model.TreatmentCluster
import uk.scimone.diafit.history.presentation.model.DayHistoryUi
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Width of the date column; the shared [HistoryTimeAxis] reserves the same space so tracks and ticks line up. */
val TrackLabelWidth: Dp = 56.dp

/**
 * One day as a track on the shared 24 h scale: glucose mountains rise from a baseline, carb and
 * bolus blocks sit in lanes just below it. Tapping expands the track into a line graph.
 */
@Composable
fun DayTrackRow(
    day: DayHistoryUi,
    thresholds: GlucoseThresholds,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(day.epochDay) { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            DateLabel(day)
            Column(Modifier.weight(1f)) {
                if (expanded) {
                    Text(summary(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    DayLineChart(day, thresholds)
                } else {
                    HorizonChart(day, thresholds)
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(1.dp))
                TreatmentStrip(day.carbs, day.dayStartUtc, day.dayEndUtc, Carbs, CARBS_FULL_INTENSITY_G, height = 4.dp)
                Spacer(Modifier.height(2.dp))
                TreatmentStrip(day.insulin, day.dayStartUtc, day.dayEndUtc, Bolus, INSULIN_FULL_INTENSITY_U, height = 4.dp)
                AnimatedVisibility(expanded) { TreatmentList(day) }
            }
        }
    }
}

@Composable
private fun DateLabel(day: DayHistoryUi) {
    Column(Modifier.width(TrackLabelWidth).padding(end = 6.dp)) {
        Text("${day.weekday} ${day.dayOfMonth}", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun summary(day: DayHistoryUi): String = listOfNotNull(
    day.timeInRangePercent?.let { "$it% in range" },
    day.totalCarbs.takeIf { it > 0f }?.let { "%.0f g".format(it) },
    day.totalInsulin.takeIf { it > 0f }?.let { "%.1f U".format(it) }
).joinToString(" · ").ifEmpty { "No data" }

@Composable
private fun TreatmentList(day: DayHistoryUi) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        day.carbs.forEach { TreatmentRow(it, "g", "%.0f", Carbs) }
        day.insulin.forEach { TreatmentRow(it, "U", "%.1f", Bolus) }
    }
}

@Composable
private fun TreatmentRow(cluster: TreatmentCluster, unit: String, valueFormat: String, color: Color) {
    val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
    val times = cluster.events.joinToString(", ") { clock.format(Date(it.timeUtc)) }
    Text("$times — ${valueFormat.format(cluster.total)} $unit", style = MaterialTheme.typography.bodySmall, color = color)
}
