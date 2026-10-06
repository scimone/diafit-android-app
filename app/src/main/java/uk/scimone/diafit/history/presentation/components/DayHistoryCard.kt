package uk.scimone.diafit.history.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.history.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.domain.model.TreatmentCluster
import uk.scimone.diafit.history.presentation.model.DayHistoryUi
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One day: collapsed it is a horizon strip plus carb/bolus strips; tapping expands it to a line graph. */
@Composable
fun DayHistoryCard(
    day: DayHistoryUi,
    thresholds: GlucoseThresholds,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(day.epochDay) { mutableStateOf(false) }

    Card(modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(12.dp)) {
            DayHeader(day, expanded)
            Spacer(Modifier.height(8.dp))
            if (expanded) {
                DayLineChart(day, thresholds)
            } else {
                HorizonChart(day, thresholds)
            }
            Spacer(Modifier.height(4.dp))
            TreatmentStrip(day.carbs, day.dayStartUtc, day.dayEndUtc, Carbs, CARBS_FULL_INTENSITY_G)
            Spacer(Modifier.height(2.dp))
            TreatmentStrip(day.insulin, day.dayStartUtc, day.dayEndUtc, Bolus, INSULIN_FULL_INTENSITY_U)
            AnimatedVisibility(expanded) {
                TreatmentList(day)
            }
        }
    }
}

@Composable
private fun DayHeader(day: DayHistoryUi, expanded: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(day.title, style = MaterialTheme.typography.titleMedium)
            Text(summary(day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand"
        )
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
private fun TreatmentRow(cluster: TreatmentCluster, unit: String, valueFormat: String, color: androidx.compose.ui.graphics.Color) {
    val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
    val times = cluster.events.joinToString(", ") { clock.format(Date(it.timeUtc)) }
    Text(
        "$times — ${valueFormat.format(cluster.total)} $unit",
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
}
