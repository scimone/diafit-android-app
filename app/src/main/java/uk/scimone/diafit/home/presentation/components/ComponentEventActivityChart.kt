package uk.scimone.diafit.home.presentation.components

import androidx.compose.foundation.shape.CircleShape
import uk.scimone.diafit.home.presentation.utils.NowDecoration
import uk.scimone.diafit.home.presentation.utils.SelectionDecoration
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
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.DrawingContext
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.Component
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
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

/** A dose/meal event: [value] is units (insulin) or grams (carbs); [durationMinutes] feeds the curve. */
data class ChartEvent(
    val time: Long,
    val value: Double,
    val durationMinutes: Int = 0
)

/** Within this distance events share one bubble (drawn at the first one's time). */
private const val BUBBLE_MERGE_WINDOW_MS = 15 * 60_000L

private fun mergeNearbyEvents(events: List<ChartEvent>): List<ChartEvent> {
    val merged = mutableListOf<ChartEvent>()
    var lastTime = Long.MIN_VALUE
    for (e in events.sortedBy { it.time }) {
        val prev = merged.lastOrNull()
        if (prev != null && e.time - lastTime <= BUBBLE_MERGE_WINDOW_MS) merged[merged.lastIndex] = prev.copy(value = prev.value + e.value)
        else merged += e
        lastTime = e.time
    }
    return merged
}

/**
 * One panel per therapy type (insulin, carbs), same design for both:
 *  - the summed **activity curve** as a soft filled area (its shape matters, not its absolute
 *    numbers, so the y labels are hidden), extended into the future to show the tail;
 *  - each **event** as a bubble *centred on the curve* with area ∝ value and a "6.8 U" /
 *    "45 g" label above it.
 *
 * Bubbles are a second [LineCartesianLayer] sharing the curve's y axis: each point is centred on
 * the curve at the event's time. Vico's point provider / data label only see `y`, so the event's
 * value is looked up from the (curve height + tiny offset) y via [BubbleValues].
 */
private const val VALUE_SCALE = 1e-4

/** Bubble y (curve height at the event + value offset) -> the event's value. */
private class BubbleValues(val byY: Map<Double, Double>) {
    fun decode(y: Double): Double = byY[y] ?: 0.0
}

private class EventBubbleProvider(
    private val component: Component,
    private val refValue: Double,
    private val values: BubbleValues
) : LineCartesianLayer.PointProvider {
    private fun diameter(value: Double): Dp = (10 + 6 * sqrt(value / refValue)).coerceIn(10.0, 30.0).dp

    override fun getPoint(entry: LineCartesianLayerModel.Entry, extraStore: ExtraStore): LineCartesianLayer.Point =
        LineCartesianLayer.Point(component, diameter(values.decode(entry.y)))

    // Deliberately constant: Vico pads each layer by half its largest point, and every Home chart
    // must end up with identical padding to stay x-aligned.
    override fun getLargestPoint(extraStore: ExtraStore) = LineCartesianLayer.Point(component, ChartPointSize)
}

@Composable
fun ComponentEventActivityChart(
    events: List<ChartEvent>,
    activityOf: (event: ChartEvent, time: Long) -> Double,
    color: Color,
    valueUnit: String,
    bubbleRefValue: Double,
    showTimeLabels: Boolean,
    nowMinute: Long,
    selectedTime: Long?,
    onSelectedTimeChange: (Long?) -> Unit,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val (alignedMinTime, _, realTime) = getTimeAxisBounds(nowMinute, hoursBack = 24)
    val maxX = timeAxisMaxX(realTime)

    val timeStepMillis = 5 * 60 * 1000L // 5 min
    val timePoints = generateSequence(alignedMinTime) { prev ->
        val next = prev + timeStepMillis
        if (next > maxX) null else next
    }.toList().let { if (it.last() != maxX) it + maxX else it }

    val activityPoints = timePoints.map { t -> t to events.sumOf { activityOf(it, t) } }
    fun activityAt(t: Long) = events.sumOf { activityOf(it, t) }
    // Events close together (a meal and its drink, a split bolus) become one bubble with their summed
    // value, so bubbles and labels never pile up on top of each other.
    val recentEvents = mergeNearbyEvents(events.filter { it.time in alignedMinTime..realTime })

    // Each bubble sits on the curve: y = curve height at the event time, nudged by a tiny
    // value-proportional offset so the value survives Vico's y-only point/label callbacks.
    val bubbleYs = recentEvents.map { activityAt(it.time) + it.value * VALUE_SCALE }
    val bubbleValues = BubbleValues(recentEvents.indices.associate { bubbleYs[it] to recentEvents[it].value })

    LaunchedEffect(activityPoints, recentEvents) {
        modelProducer.runTransaction {
            lineSeries {
                series(x = activityPoints.map { it.first }, y = activityPoints.map { it.second })
            }
            if (recentEvents.isNotEmpty()) {
                lineSeries {
                    series(x = recentEvents.map { it.time }, y = bubbleYs)
                }
            }
        }
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val maxActivity = (activityPoints.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(0.001)
    val maxY = maxActivity * 1.5 // headroom for bubbles + labels sitting on the curve

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
        rangeProvider = createTimeAxisRangeProvider(
            minX = alignedMinTime, maxX = maxX, minY = 0.0, maxY = maxY
        )
    )

    val bubbleComponent = rememberShapeComponent(
        fill = Fill(color.copy(alpha = 0.85f)),
        shape = CircleShape,
        strokeFill = Fill(color),
        strokeThickness = 1.5.dp
    )
    val labelStyle = TextStyle(color = onSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val bubbleLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.dp),
                pointProvider = remember(bubbleComponent, bubbleRefValue, bubbleValues) {
                    EventBubbleProvider(bubbleComponent, bubbleRefValue, bubbleValues)
                },
                dataLabel = rememberTextComponent(style = labelStyle),
                dataLabelPosition = Position.Vertical.Top,
                dataLabelValueFormatter = remember(valueUnit, bubbleValues) {
                    CartesianValueFormatter { _, value, _ ->
                        val v = Math.round(bubbleValues.decode(value) * 10) / 10.0
                        (if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()) + " " + valueUnit
                    }
                }
            )
        ),
        pointSpacing = ChartPointSpacing,
        verticalAxisPosition = Axis.Position.Vertical.Start,
        rangeProvider = createTimeAxisRangeProvider(minX = alignedMinTime, maxX = maxX, minY = 0.0, maxY = maxY)
    )

    val chart = rememberCartesianChart(
        *listOfNotNull(curveLayer, bubbleLayer.takeIf { recentEvents.isNotEmpty() }).toTypedArray(),
        startAxis = VerticalAxis.rememberStart(
            label = null,
            tick = null,
            itemPlacer = remember { VerticalAxis.ItemPlacer.count({ 2 }) },
            guideline = rememberLineComponent(fill = Fill(onSurface), thickness = 0.1.dp),
            size = SharedStartAxisSize
        ),
        bottomAxis = rememberTimeBottomAxis(showLabels = showTimeLabels),
        layerPadding = { LineChartLayerPadding },
        decorations = listOfNotNull(
            NowDecoration(
                nowX = realTime.toDouble(),
                lineColor = onSurface.copy(alpha = 0.7f),
                washColor = MaterialTheme.colorScheme.background.copy(alpha = 0.55f)
            ),
            selectedTime?.let { SelectionDecoration(it.toDouble(), onSurface.copy(alpha = 0.9f)) }
        ),
        getXStep = { _ -> getTimeAxisXStep() },
        marker = remember { object : CartesianMarker {} },
        markerVisibilityListener = remember(onSelectedTimeChange) {
            object : CartesianMarkerVisibilityListener {
                override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) =
                    onSelectedTimeChange(targets.firstOrNull()?.x?.toLong())
                override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) =
                    onSelectedTimeChange(targets.firstOrNull()?.x?.toLong())
                override fun onHidden(marker: CartesianMarker) = onSelectedTimeChange(null)
            }
        },
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        zoomState = zoomState,
        scrollState = scrollState
    )
}
