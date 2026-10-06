package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
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
private class HorizonBandStyle(val level: (BandLevels) -> Float, val color: Color)

/**
 * Opaque, drawn in order so each deeper band overlays the shallower one. They are the CGM line's
 * high/low colours: the mild band slightly muted, the "very" band at full strength.
 */
private val HORIZON_BANDS = listOf(
    HorizonBandStyle({ it.high }, lerp(AboveRange, Color.Black, 0.3f)),
    HorizonBandStyle({ it.veryHigh }, AboveRange),
    HorizonBandStyle({ it.low }, lerp(BelowRange, Color.Black, 0.3f)),
    HorizonBandStyle({ it.veryLow }, BelowRange)
)

/** Thickness of the green strip along the bottom that marks "glucose was recorded and in range". */
private val IN_RANGE_STRIP_HEIGHT = 5.dp

/**
 * A day of glucose folded into a thin panel: a green strip along the bottom wherever data exists,
 * with high/low excursions rising from the bottom edge in front of it, deepening in colour the
 * further out of range they go.
 */
@Composable
fun HorizonChart(
    day: DayHistoryUi,
    thresholds: GlucoseThresholds,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp
) {
    val runs = day.glucose.splitAtGaps()
    Canvas(modifier.fillMaxWidth().height(height).background(stripBackground())) {
        val axis = DayXAxis(day.dayStartUtc, day.dayEndUtc, size.width)
        val stripHeight = IN_RANGE_STRIP_HEIGHT.toPx()
        runs.filter { it.size > 1 }.forEach { run ->
            val left = axis.x(run.first().timeUtc)
            drawRect(InRange, Offset(left, size.height - stripHeight), Size(axis.x(run.last().timeUtc) - left, stripHeight))
            HORIZON_BANDS.forEach { band ->
                drawMountain(run, axis, { band.level(thresholds.levels(it.mgdl)) }, band.color)
            }
        }
    }
}

private fun DrawScope.drawMountain(
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
