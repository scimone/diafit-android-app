package uk.scimone.diafit.history.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.history.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.domain.model.TreatmentCluster
import uk.scimone.diafit.history.presentation.model.DayHistoryUi
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One day on the shared 24 h scale, as three stacked strips on the page background: glucose
 * (green strip with high/low mountains), carbs and bolus. No lines or cards: each strip is just a
 * slightly lighter field. Tapping expands the glucose strip into a line graph.
 */
@Composable
fun DayTrackRow(
    day: DayHistoryUi,
    thresholds: GlucoseThresholds,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(day.epochDay) { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 5.dp)) {
        Box {
            if (expanded) DayLineChart(day, thresholds) else HorizonChart(day, thresholds)
            Text(
                caption(day, expanded),
                Modifier.padding(start = 6.dp, top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(2.dp))
        TreatmentStrip(day.carbs, day.dayStartUtc, day.dayEndUtc, Carbs, CARBS_FULL_INTENSITY_G)
        Spacer(Modifier.height(2.dp))
        TreatmentStrip(day.insulin, day.dayStartUtc, day.dayEndUtc, Bolus, INSULIN_FULL_INTENSITY_U)
        AnimatedVisibility(expanded) { TreatmentList(day) }
    }
}

/** "Tue 6", and once expanded the day's totals as well. */
private fun caption(day: DayHistoryUi, expanded: Boolean): String {
    val date = "${day.weekday} ${day.dayOfMonth}"
    return if (expanded) "$date · ${summary(day)}" else date
}

private fun summary(day: DayHistoryUi): String = listOfNotNull(
    day.timeInRangePercent?.let { "$it% in range" },
    day.totalCarbs.takeIf { it > 0f }?.let { "%.0f g".format(it) },
    day.totalInsulin.takeIf { it > 0f }?.let { "%.1f U".format(it) }
).joinToString(" · ").ifEmpty { "No data" }

@Composable
private fun TreatmentList(day: DayHistoryUi) {
    Column(Modifier.padding(horizontal = 8.dp).padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
