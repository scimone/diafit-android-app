package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.history.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.presentation.model.DayHistoryUi
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.InRange

/** Width of the date column left of every track; the time axis is inset by the same amount. */
val DAY_LABEL_WIDTH = 40.dp

/** Width of the time-in-range column right of every track; the time axis is inset by the same amount. */
val DAY_SUMMARY_WIDTH = 48.dp

/** A day with at least this share in range gets its percentage in the in-range colour (consensus target). */
private const val TIR_GOAL = 0.7

/**
 * One day on the shared 24 h scale: the date, then one rounded track made of three bands (glucose
 * horizon with high/low mountains, carbs, bolus), then the day's time in range. Tapping opens the day.
 */
@Composable
fun DayTrackRow(
    day: DayHistoryUi,
    thresholds: GlucoseThresholds,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayLabel(day, isToday)
        Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))) {
            HorizonChart(day, thresholds)
            Spacer(Modifier.height(1.dp))
            TreatmentStrip(day.carbs, day.dayStartUtc, day.dayEndUtc, Carbs, CARBS_FULL_INTENSITY_G)
            Spacer(Modifier.height(1.dp))
            TreatmentStrip(day.insulin, day.dayStartUtc, day.dayEndUtc, Bolus, INSULIN_FULL_INTENSITY_U)
        }
        DaySummary(day)
    }
}

@Composable
private fun DayLabel(day: DayHistoryUi, isToday: Boolean) {
    val color = when {
        isToday -> MaterialTheme.colorScheme.primary
        day.hasData -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    Column(Modifier.width(DAY_LABEL_WIDTH), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(day.weekday.uppercase(), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = if (isToday) 1f else 0.7f))
        Text(day.dayOfMonth, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

/** The day's time in range as a number over a tiny below/in/above bar. */
@Composable
private fun DaySummary(day: DayHistoryUi) {
    Column(Modifier.width(DAY_SUMMARY_WIDTH).padding(start = 8.dp), horizontalAlignment = Alignment.End) {
        val tir = day.inRangeShare
        if (tir == null) {
            Text("–", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            return@Column
        }
        Text(
            "${day.timeInRangePercent}%",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (tir >= TIR_GOAL) InRange else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))) {
            listOf((day.belowShare ?: 0.0) to BelowRange, tir to InRange, (day.aboveShare ?: 0.0) to AboveRange).forEach { (share, color) ->
                if (share > 0.0) Box(Modifier.weight(share.toFloat()).fillMaxHeight().background(color))
            }
        }
    }
}
