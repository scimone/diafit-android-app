package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.scimone.diafit.history.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.presentation.model.DayHistoryUi
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.InRange
import kotlin.math.max
import kotlin.math.min

private const val Y_MIN = 40f
private const val Y_MAX_FLOOR = 250f

/** The expanded form of a [HorizonChart]: the day's glucose as a line, coloured by range, with the target band. */
@Composable
fun DayLineChart(
    day: DayHistoryUi,
    thresholds: GlucoseThresholds,
    modifier: Modifier = Modifier
) {
    val labelStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    val textMeasurer = rememberTextMeasurer()
    val runs = day.glucose.splitAtGaps()
    val yMax = max(Y_MAX_FLOOR, (day.glucose.maxOfOrNull { it.mgdl } ?: 0) + 10f)

    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        val plotHeight = size.height
        val axis = DayXAxis(day.dayStartUtc, day.dayEndUtc, size.width)
        fun y(mgdl: Float) = plotHeight - (mgdl - Y_MIN) / (yMax - Y_MIN) * plotHeight

        drawRect(
            InRange.copy(alpha = 0.10f),
            Offset(0f, y(thresholds.high.toFloat())),
            Size(size.width, y(thresholds.low.toFloat()) - y(thresholds.high.toFloat()))
        )
        listOf(thresholds.low, thresholds.high).forEach { bound ->
            drawText(textMeasurer, "$bound", Offset(4.dp.toPx(), y(bound.toFloat()) - 13.dp.toPx()), labelStyle)
        }


        runs.forEach { run ->
            run.zipWithNext().forEach { (a, b) ->
                val color = when {
                    (a.mgdl + b.mgdl) / 2f > thresholds.high -> AboveRange
                    (a.mgdl + b.mgdl) / 2f < thresholds.low -> BelowRange
                    else -> InRange
                }
                drawLine(
                    color,
                    Offset(axis.x(a.timeUtc), y(min(a.mgdl.toFloat(), yMax))),
                    Offset(axis.x(b.timeUtc), y(min(b.mgdl.toFloat(), yMax))),
                    2.dp.toPx(), StrokeCap.Round
                )
            }
        }
    }
}
