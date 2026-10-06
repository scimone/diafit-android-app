package uk.scimone.diafit.history.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * One day as a dark panel on the shared 24 h scale: a caption with the date, glucose mountains
 * rising from the bottom of the chart, carb and bolus blocks in lanes below. Tapping expands the
 * panel into a line graph.
 */
@Composable
fun DayTrackRow(
    day: DayHistoryUi,
    thresholds: GlucoseThresholds,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(day.epochDay) { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { expanded = !expanded }
            .padding(bottom = 6.dp)
    ) {
        Text(
            caption(day, expanded),
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (expanded) DayLineChart(day, thresholds) else HorizonChart(day, thresholds)
        Spacer(Modifier.height(4.dp))
        TreatmentStrip(day.carbs, day.dayStartUtc, day.dayEndUtc, Carbs, CARBS_FULL_INTENSITY_G, height = 6.dp)
        Spacer(Modifier.height(2.dp))
        TreatmentStrip(day.insulin, day.dayStartUtc, day.dayEndUtc, Bolus, INSULIN_FULL_INTENSITY_U, height = 6.dp)
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
    Column(Modifier.padding(horizontal = 10.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
