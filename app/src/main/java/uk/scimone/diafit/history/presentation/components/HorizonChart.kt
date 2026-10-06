package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.history.domain.model.BandLevels
import uk.scimone.diafit.history.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.presentation.model.DayHistoryUi
import uk.scimone.diafit.history.presentation.model.GlucosePoint
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.InRange

/** One out-of-range band of the horizon: how deep a reading reaches into it, and how it is tinted. */
private class HorizonBandStyle(val level: (BandLevels) -> Float, val color: Color, val alpha: Float)

private val HORIZON_BANDS = listOf(
    HorizonBandStyle({ it.high }, AboveRange, 0.55f),
    HorizonBandStyle({ it.veryHigh }, AboveRange, 1f),
    HorizonBandStyle({ it.low }, BelowRange, 0.55f),
    HorizonBandStyle({ it.veryLow }, BelowRange, 1f)
)

/** Height, as a fraction of the chart, of the strip that marks "glucose was recorded and in range". */
private const val IN_RANGE_BASELINE = 0.2f

/**
 * A day of glucose folded into a thin strip: an in-range baseline wherever data exists, with
 * high/low excursions stacked on top as bands that deepen in colour the further out of range they go.
 */
@Composable
fun HorizonChart(
    day: DayHistoryUi,
    thresholds: GlucoseThresholds,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val runs = day.glucose.splitAtGaps()
    Canvas(modifier.fillMaxWidth().height(height)) {
        val axis = DayXAxis(day.dayStartUtc, day.dayEndUtc, size.width)
        drawHourGrid(axis, day.dayStartUtc, gridColor)
        runs.filter { it.size > 1 }.forEach { run ->
            drawArea(run, axis, { IN_RANGE_BASELINE }, InRange.copy(alpha = 0.35f))
            HORIZON_BANDS.forEach { band ->
                drawArea(run, axis, { band.level(thresholds.levels(it.mgdl)) }, band.color.copy(alpha = band.alpha))
            }
        }
    }
}

private fun DrawScope.drawArea(
    run: List<GlucosePoint>,
    axis: DayXAxis,
    levelOf: (GlucosePoint) -> Float,
    color: Color
) {
    if (run.none { levelOf(it) > 0f }) return
    val path = Path().apply {
        moveTo(axis.x(run.first().timeUtc), size.height)
        run.forEach { lineTo(axis.x(it.timeUtc), size.height * (1f - levelOf(it))) }
        lineTo(axis.x(run.last().timeUtc), size.height)
        close()
    }
    drawPath(path, color)
}

/** Faint vertical lines every 6 hours of the day, so strips of different days can be read against each other. */
internal fun DrawScope.drawHourGrid(axis: DayXAxis, dayStartUtc: Long, color: Color) {
    for (hour in GRID_HOURS) {
        val x = axis.x(dayStartUtc + hour * 3_600_000L)
        drawLine(color, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
    }
}

private val GRID_HOURS = listOf(6, 12, 18)
