package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.presentation.model.DayHistoryUi
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs

/**
 * One day on the shared 24 h scale, using the full width: one rounded track made of three bands
 * (glucose horizon with high/low mountains, carbs, bolus) with the date as a caption in its corner.
 * Tapping opens the day.
 */
@Composable
fun DayTrackRow(
    day: DayHistoryUi,
    thresholds: GlucoseThresholds,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box {
            HorizonChart(day, thresholds)
            Text(
                "${day.weekday} ${day.dayOfMonth}",
                Modifier.padding(start = 6.dp, top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(1.dp))
        TreatmentStrip(day.carbs, day.dayStartUtc, day.dayEndUtc, Carbs, CARBS_FULL_INTENSITY_G)
        Spacer(Modifier.height(1.dp))
        TreatmentStrip(day.insulin, day.dayStartUtc, day.dayEndUtc, Bolus, INSULIN_FULL_INTENSITY_U)
    }
}
