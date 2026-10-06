package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
private val POINT_RADIUS = 1.2.dp

/** The expanded form of a [HorizonChart]: the day's readings as a scatter plot, coloured by range, over a grey target band (as on Home). */
@Composable
fun DayLineChart(
    day: DayHistoryUi,
    thresholds: GlucoseThresholds,
    modifier: Modifier = Modifier
) {
    val labelStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    val textMeasurer = rememberTextMeasurer()
    val targetBandColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val yMax = max(Y_MAX_FLOOR, (day.glucose.maxOfOrNull { it.mgdl } ?: 0) + 10f)

    Canvas(modifier.fillMaxWidth().height(180.dp).background(stripBackground())) {
        val plotHeight = size.height
        val axis = DayXAxis(day.dayStartUtc, day.dayEndUtc, size.width)
        fun y(mgdl: Float) = plotHeight - (mgdl - Y_MIN) / (yMax - Y_MIN) * plotHeight

        drawRect(
            targetBandColor,
            Offset(0f, y(thresholds.high.toFloat())),
            Size(size.width, y(thresholds.low.toFloat()) - y(thresholds.high.toFloat()))
        )
        listOf(thresholds.low, thresholds.high).forEach { bound ->
            drawText(textMeasurer, "$bound", Offset(4.dp.toPx(), y(bound.toFloat()) - 13.dp.toPx()), labelStyle)
        }


        day.glucose.forEach { reading ->
            val color = when {
                reading.mgdl > thresholds.high -> AboveRange
                reading.mgdl < thresholds.low -> BelowRange
                else -> InRange
            }
            drawCircle(color, POINT_RADIUS.toPx(), Offset(axis.x(reading.timeUtc), y(min(reading.mgdl.toFloat(), yMax))))
        }
    }
}
