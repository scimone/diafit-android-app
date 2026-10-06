package uk.scimone.diafit.home.presentation.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.VicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.Component
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import uk.scimone.diafit.core.domain.model.InsulinActivity
import uk.scimone.diafit.home.presentation.model.InsulinActivityChartData
import uk.scimone.diafit.home.presentation.utils.ChartPointSize
import uk.scimone.diafit.home.presentation.utils.ChartPointSpacing
import uk.scimone.diafit.home.presentation.utils.LineChartLayerPadding
import uk.scimone.diafit.home.presentation.utils.SharedStartAxisSize
import uk.scimone.diafit.home.presentation.utils.createTimeAxisRangeProvider
import uk.scimone.diafit.home.presentation.utils.getTimeAxisBounds
import uk.scimone.diafit.home.presentation.utils.getTimeAxisXStep
import uk.scimone.diafit.home.presentation.utils.rememberTimeBottomAxis
import uk.scimone.diafit.home.presentation.utils.timeAxisMaxX
import kotlin.math.sqrt

/**
 * Everything bolus-related in one panel:
 *  - the **insulin activity curve** (summed over all boluses, extended into the future so the
 *    decay is visible) as a soft filled area — its shape matters, not its absolute numbers, so the
 *    y-axis labels are hidden;
 *  - each **bolus** as a bubble on a rail near the top of the panel, with *area ∝ units* and a
 *    "4.5 U" label, so the dose is readable at a glance and the bubble's x position is the dose
 *    time (it lines up with the CGM chart above).
 *
 * The bubbles live on their own layer bound to the (axis-less) end axis with a fixed 0..1 range, so
 * they don't depend on the activity curve's scale. Vico's data labels only receive the y value, so
 * the units are encoded as a tiny offset on top of [RAIL_Y] ([UNITS_SCALE] per unit, invisible on
 * screen) and decoded again by the point provider and label formatter.
 */
private const val RAIL_Y = 0.8
private const val UNITS_SCALE = 1e-4

private fun decodeUnits(y: Double): Double = ((y - RAIL_Y) / UNITS_SCALE).coerceAtLeast(0.0)

private fun bubbleSize(units: Double): Dp = (10 + 6 * sqrt(units)).coerceIn(10.0, 32.0).dp

private class BolusBubbleProvider(private val component: Component) : LineCartesianLayer.PointProvider {
    override fun getPoint(entry: com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel.Entry, extraStore: ExtraStore) =
        LineCartesianLayer.Point(component, bubbleSize(decodeUnits(entry.y)))

    // Deliberately constant (not the real max bubble size): Vico pads the layer by half the largest
    // point, and every Home chart must end up with identical padding to stay x-aligned.
    override fun getLargestPoint(extraStore: ExtraStore) = LineCartesianLayer.Point(component, ChartPointSize)
}

@Composable
fun ComponentInsulinActivityChart(
    values: List<InsulinActivityChartData>,
    color: Color,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val (alignedMinTime, _, realTime) = getTimeAxisBounds(hoursBack = 24)
    val maxX = timeAxisMaxX(realTime)

    val timeStepMillis = 5 * 60 * 1000L // 5 min

    val timePoints = generateSequence(alignedMinTime) { prev ->
        val next = prev + timeStepMillis
        if (next > maxX) null else next
    }.toList()

    val activityPoints = timePoints.map { timePoint ->
        val totalActivity = values.sumOf { bolus ->
            val insulinEffect = InsulinActivity.calculate(
                bolusAmount = bolus.value.toDouble(),
                bolusTime = bolus.timeLong,
                time = timePoint
            ).activity
            if (timePoint >= bolus.timeLong && insulinEffect > 0) insulinEffect else 0.0
        }
        timePoint to totalActivity
    }

    val recentBoluses = values.filter { it.timeLong in alignedMinTime..realTime }

    LaunchedEffect(activityPoints, recentBoluses) {
        modelProducer.runTransaction {
            lineSeries {
                series(x = activityPoints.map { it.first }, y = activityPoints.map { it.second })
            }
            if (recentBoluses.isNotEmpty()) {
                lineSeries {
                    series(
                        x = recentBoluses.map { it.timeLong },
                        y = recentBoluses.map { RAIL_Y + it.value * UNITS_SCALE }
                    )
                }
            }
        }
    }

    if (activityPoints.isNotEmpty()) {
        val onSurface = MaterialTheme.colorScheme.onSurface
        val maxActivity = (activityPoints.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(0.001)

        val curveLayer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(color)),
                    stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp),
                    areaFill = LineCartesianLayer.AreaFill.single(Fill(color.copy(alpha = 0.25f)))
                )
            ),
            pointSpacing = ChartPointSpacing,
            verticalAxisPosition = Axis.Position.Vertical.Start,
            // Headroom so the curve stays in the lower ~55% and never collides with the bubble rail.
            rangeProvider = createTimeAxisRangeProvider(
                minX = alignedMinTime,
                maxX = maxX,
                minY = 0.0,
                maxY = maxActivity * 1.8
            )
        )

        val bubbleComponent = rememberShapeComponent(
            fill = Fill(color.copy(alpha = 0.85f)),
            shape = CircleShape,
            strokeFill = Fill(color),
            strokeThickness = 1.5.dp
        )
        val bubbleLayer = rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                    stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.dp),
                    pointProvider = remember(bubbleComponent) { BolusBubbleProvider(bubbleComponent) },
                    dataLabel = rememberTextComponent(
                        style = TextStyle(color = onSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    ),
                    dataLabelPosition = Position.Vertical.Top,
                    dataLabelValueFormatter = remember {
                        CartesianValueFormatter { _, value, _ ->
                            val units = Math.round(decodeUnits(value) * 10) / 10.0
                            (if (units % 1.0 == 0.0) units.toInt().toString() else units.toString()) + " U"
                        }
                    }
                )
            ),
            pointSpacing = ChartPointSpacing,
            verticalAxisPosition = Axis.Position.Vertical.End,
            rangeProvider = createTimeAxisRangeProvider(
                minX = alignedMinTime,
                maxX = maxX,
                minY = 0.0,
                maxY = 1.0
            )
        )

        val chart = rememberCartesianChart(
            *listOfNotNull(curveLayer, bubbleLayer.takeIf { recentBoluses.isNotEmpty() }).toTypedArray(),
            startAxis = VerticalAxis.rememberStart(
                label = null,
                tick = null,
                itemPlacer = remember { VerticalAxis.ItemPlacer.count({ 2 }) },
                guideline = rememberLineComponent(fill = Fill(onSurface), thickness = 0.1.dp),
                size = SharedStartAxisSize
            ),
            bottomAxis = rememberTimeBottomAxis(),
            layerPadding = { LineChartLayerPadding },
            getXStep = { _ -> getTimeAxisXStep() },
        )

        CartesianChartHost(
            chart = chart,
            modelProducer = modelProducer,
            zoomState = zoomState,
            scrollState = scrollState
        )
    }
}
