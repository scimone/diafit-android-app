package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.ActivitySpan
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.presentation.model.DayHistoryUi
import uk.scimone.diafit.history.presentation.model.HistoryLayers

/**
 * One day on the shared 24 h scale: the date as a caption above, then one rounded tile. Glucose is the horizon
 * chart on top; under it, as far as [layers] allow, a lane of carbs and insulin and a lane of activity. Time asleep
 * is a grey wash behind the whole tile. Tapping opens the day.
 */
@Composable
fun DayTrackRow(
    day: DayHistoryUi,
    thresholds: GlucoseThresholds,
    isToday: Boolean,
    layers: HistoryLayers,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessions = remember(day.activity) { day.activity.sleepSessions.map { it.startUtc to it.endUtc } }
    val workouts = remember(day.activity) { day.activity.exercise.map { ActivitySpan(it.startUtc, it.endUtc, 1f) } }
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick)
    ) {
        Text(
            "${day.weekday} ${day.dayOfMonth}",
            Modifier.padding(start = 4.dp, bottom = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(stripBackground())
                .then(if (layers.sleep) Modifier.sleepWash(sessions, day.dayStartUtc, day.dayEndUtc) else Modifier)
        ) {
            HorizonChart(day, thresholds, drawBackground = false)
            if (layers.treatments) {
                Spacer(Modifier.height(2.dp))
                TreatmentLane(day.carbs, day.insulin, day.dayStartUtc, day.dayEndUtc)
            }
            if (layers.activity) {
                Spacer(Modifier.height(2.dp))
                ActivityLane(day.elevatedActivity, workouts, day.dayStartUtc, day.dayEndUtc)
            }
            if (layers.treatments || layers.activity) Spacer(Modifier.height(3.dp))
        }
    }
}
