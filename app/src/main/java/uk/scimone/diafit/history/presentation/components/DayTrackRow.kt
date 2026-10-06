package uk.scimone.diafit.history.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
 * One day as a full-width band on the shared 24 h scale. Bands alternate between two background
 * tints ([striped]) instead of using divider lines; glucose mountains rise from the bottom of the
 * chart with carb and bolus blocks in lanes below, and the date is a small caption in the corner.
 * Tapping expands the band into a line graph.
 */
@Composable
fun DayTrackRow(
    day: DayHistoryUi,
    thresholds: GlucoseThresholds,
    striped: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(day.epochDay) { mutableStateOf(false) }
    val background = if (striped) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background

    Box(modifier.fillMaxWidth().background(background).clickable { expanded = !expanded }) {
        Column(Modifier.padding(top = if (expanded) CAPTION_HEIGHT else 0.dp, bottom = 2.dp)) {
            if (expanded) DayLineChart(day, thresholds) else HorizonChart(day, thresholds)
            Spacer(Modifier.height(1.dp))
            TreatmentStrip(day.carbs, day.dayStartUtc, day.dayEndUtc, Carbs, CARBS_FULL_INTENSITY_G, height = 4.dp)
            Spacer(Modifier.height(1.dp))
            TreatmentStrip(day.insulin, day.dayStartUtc, day.dayEndUtc, Bolus, INSULIN_FULL_INTENSITY_U, height = 4.dp)
            AnimatedVisibility(expanded) { TreatmentList(day) }
        }
        Text(
            caption(day, expanded),
            Modifier.padding(start = 8.dp, top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val CAPTION_HEIGHT = 20.dp

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
