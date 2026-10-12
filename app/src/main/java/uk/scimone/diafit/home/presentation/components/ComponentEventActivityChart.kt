package uk.scimone.diafit.home.presentation.components

import androidx.compose.foundation.shape.CircleShape
import uk.scimone.diafit.home.presentation.utils.BubbleHighlightDecoration
import uk.scimone.diafit.home.presentation.utils.NowDecoration
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
import uk.scimone.diafit.home.presentation.utils.ChartXSpacing
import uk.scimone.diafit.home.presentation.utils.ChartPointSize
import uk.scimone.diafit.home.presentation.utils.ChartPointSpacing
import uk.scimone.diafit.home.presentation.utils.LineChartLayerPadding
import uk.scimone.diafit.home.presentation.utils.SharedStartAxisSize
import uk.scimone.diafit.home.presentation.utils.createTimeAxisRangeProvider
import uk.scimone.diafit.home.presentation.utils.ChartTimeWindow
import uk.scimone.diafit.home.presentation.utils.getTimeAxisXStep
import uk.scimone.diafit.home.presentation.utils.rememberTimeBottomAxis
import kotlin.math.abs
import kotlin.math.sqrt

/** A dose/meal event: [value] is units (insulin) or grams (carbs); [durationMinutes] feeds the insulin curve, [curve] the carb one. */
data class ChartEvent(
    val time: Long,
    val value: Double,
    val durationMinutes: Int = 0,
    val curve: uk.scimone.diafit.mealactivity.domain.MealActivityCurve? = null
)

/**
 * Within this distance of the previous event, events share one bubble (drawn at the first one's
 * time). Chained, so a long meal with a plate and a small bolus every 10 minutes becomes one
 * labelled bubble; every individual event is also marked by a small tick dot on the curve.
 */
private const val BUBBLE_MERGE_WINDOW_MS = 15 * 60_000L
private val TickDotSize = 5.dp

/** Bubbles closer than this many dp on screen overlap, so they merge however wide the zoom. */
private const val BUBBLE_MIN_GAP_DP = 30f

/** One drawn bubble: the [members] it stands for, at their amount-weighted mean time. */
data class EventBubble(val time: Long, val value: Double, val members: List<ChartEvent>, val diameterDp: Float) {
    val memberTimes: List<Long> get() = members.map { it.time }
}

private fun mergeNearbyEvents(events: List<ChartEvent>, windowMs: Long, refValue: Double): List<EventBubble> {
    val groups = mutableListOf<MutableList<ChartEvent>>()
    var lastTime = Long.MIN_VALUE
    for (e in events.sortedBy { it.time }) {
        if (groups.isNotEmpty() && e.time - lastTime <= windowMs) groups.last() += e else groups += mutableListOf(e)
        lastTime = e.time
    }
    return groups.map { g ->
        val total = g.sumOf { it.value }
        // Weighted by amount, so a 60 g plate and a 5 g sip put the bubble near the plate.
        val time = if (total > 0.0) (g.sumOf { it.time.toDouble() * it.value } / total).toLong()
        else g.map { it.time }.average().toLong()
        EventBubble(time, total, g.toList(), bubbleDiameter(total, refValue).value)
    }
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

/** Events within this distance of the inspection cursor count as caught by it (matches Home's readout). */
private const val HIGHLIGHT_NEAR_MS = 15 * 60_000L

/** Approximate height of a bubble's value label, for y-range headroom. */
private const val BUBBLE_LABEL_DP = 14f
/** Vico's inset around the plot area of a panel, subtracted from the panel height. */
private const val PLOT_INSET_DP = 10f

/** Bubble y (curve height at the event + value offset) -> the event's value. */
private class BubbleValues(val byY: Map<Double, Double>) {
    fun decode(y: Double): Double = byY[y] ?: 0.0
}

private fun bubbleDiameter(value: Double, refValue: Double): Dp = (10 + 6 * sqrt(value / refValue)).coerceIn(10.0, 30.0).dp

private class EventBubbleProvider(
    private val component: Component,
    private val refValue: Double,
    private val values: BubbleValues
) : LineCartesianLayer.PointProvider {
    override fun getPoint(entry: LineCartesianLayerModel.Entry, extraStore: ExtraStore): LineCartesianLayer.Point =
        LineCartesianLayer.Point(component, bubbleDiameter(values.decode(entry.y), refValue))

    // Deliberately constant: Vico pads each layer by half its largest point, and every Home chart
    // must end up with identical padding to stay x-aligned.
    override fun getLargestPoint(extraStore: ExtraStore) = LineCartesianLayer.Point(component, ChartPointSize)
}

/** Small dots marking each individual event on the curve. */
private class TickProvider(private val component: Component) : LineCartesianLayer.PointProvider {
    override fun getPoint(entry: LineCartesianLayerModel.Entry, extraStore: ExtraStore): LineCartesianLayer.Point =
        LineCartesianLayer.Point(component, TickDotSize)

    // Constant and equal to the other layers', see EventBubbleProvider.getLargestPoint.
    override fun getLargestPoint(extraStore: ExtraStore) = LineCartesianLayer.Point(component, ChartPointSize)
}

@Composable
fun ComponentEventActivityChart(
    /** Height of the panel the chart fills, for fitting the bubbles' headroom into the y range. */
    plotHeightDp: Float,
    events: List<ChartEvent>,
    activityOf: (event: ChartEvent, time: Long) -> Double,
    color: Color,
    valueUnit: String,
    bubbleRefValue: Double,
    showTimeLabels: Boolean,
    window: ChartTimeWindow,
    scrollState: VicoScrollState,
    zoomState: VicoZoomState,
    /** Inspection cursor time: bubbles standing for events near it get a ring. */
    highlightTime: Long? = null,
    /** Times of individual events whose bubble should be ringed (a selection made elsewhere, e.g. a tapped meal photo). */
    highlightEventTimes: Set<Long> = emptySet(),
    /** Receives the bubbles currently drawn, so a screen can hit-test taps against them. */
    onBubbles: ((List<EventBubble>) -> Unit)? = null,
    /** Receives the chart's y scale (activity per dp) so another panel can draw its curve at the same scale. */
    onScale: ((unitsPerDp: Double) -> Unit)? = null
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    val alignedMinTime = window.minX
    val realTime = window.now
    val maxX = window.maxX

    val timeStepMillis = 5 * 60 * 1000L // 5 min
    val timePoints = generateSequence(alignedMinTime) { prev ->
        val next = prev + timeStepMillis
        if (next > maxX) null else next
    }.toList().let { if (it.last() != maxX) it + maxX else it }

    val activityPoints = timePoints.map { t -> t to events.sumOf { activityOf(it, t) } }
    fun activityAt(t: Long) = events.sumOf { activityOf(it, t) }
    // Events close together (a meal and its drink, a split bolus) become one bubble with their summed
    // value, so bubbles and labels never pile up on top of each other.
    val rawRecentEvents = events.filter { it.time in alignedMinTime..realTime }.sortedBy { it.time }
    // At wide zoom 15 min is a few dp, so the merge distance grows to keep bubbles apart on screen.
    val dpPerHour = ChartXSpacing.value * zoomState.value.coerceAtLeast(0.01f)
    val mergeWindowMs = maxOf(BUBBLE_MERGE_WINDOW_MS, (BUBBLE_MIN_GAP_DP / dpPerHour * 3_600_000f).toLong())
    val recentEvents = mergeNearbyEvents(rawRecentEvents, mergeWindowMs, bubbleRefValue)
    // Every individual event gets a dot on the curve, merged or not.
    val tickEvents = rawRecentEvents

    // Each bubble sits on the curve: y = curve height at the event time, nudged by a tiny
    // value-proportional offset so the value survives Vico's y-only point/label callbacks.
    val bubbleYs = recentEvents.map { activityAt(it.time) + it.value * VALUE_SCALE }
    val bubbleValues = BubbleValues(recentEvents.indices.associate { bubbleYs[it] to recentEvents[it].value })

    // The curve is split at "now": the past is drawn normally, the forecast tail (shared point at now
    // keeps the line continuous) in a faded variant. Bubbles and CGM points are never faded.
    val nowPoint = realTime to activityAt(realTime)
    val pastPoints = activityPoints.filter { it.first < realTime } + nowPoint
    val futurePoints = listOf(nowPoint) + activityPoints.filter { it.first > realTime }

    if (onBubbles != null) {
        val report = androidx.compose.runtime.rememberUpdatedState(onBubbles)
        LaunchedEffect(recentEvents) { report.value(recentEvents) }
    }

    LaunchedEffect(activityPoints, recentEvents, tickEvents) {
        modelProducer.runTransaction {
            lineSeries {
                series(x = pastPoints.map { it.first }, y = pastPoints.map { it.second })
                series(x = futurePoints.map { it.first }, y = futurePoints.map { it.second })
            }
            if (tickEvents.isNotEmpty()) {
                lineSeries {
                    series(x = tickEvents.map { it.time }, y = tickEvents.map { activityAt(it.time) })
                }
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
    // Vico clips each layer to the plot area, so bubbles centred on the curve would be cut off at the
    // top (label + upper half) and at the bottom (events at zero activity). Widen the y range so the
    // biggest bubble fits entirely: reserve its radius below the baseline and radius + label above the peak.
    val maxBubbleDp = recentEvents.maxOfOrNull { bubbleDiameter(it.value, bubbleRefValue).value } ?: 0f
    val reserveBelowDp = maxBubbleDp / 2f
    val reserveAboveDp = if (maxBubbleDp > 0f) maxBubbleDp / 2f + BUBBLE_LABEL_DP else 0f
    val plotDp = (plotHeightDp - PLOT_INSET_DP).coerceAtLeast(30f)
    val curveDp = (plotDp - reserveBelowDp - reserveAboveDp).coerceAtLeast(plotDp / 3f)
    val unitsPerDp = maxActivity * 1.1 / curveDp
    if (onScale != null) {
        val report = androidx.compose.runtime.rememberUpdatedState(onScale)
        LaunchedEffect(unitsPerDp) { report.value(unitsPerDp) }
    }
    val minY = -reserveBelowDp * unitsPerDp
    val maxY = maxActivity * 1.1 + reserveAboveDp * unitsPerDp

    val curveLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(color)),
                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp),
                areaFill = LineCartesianLayer.AreaFill.single(Fill(color.copy(alpha = 0.25f)))
            ),
            LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(color.copy(alpha = 0.45f))),
                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp),
                areaFill = LineCartesianLayer.AreaFill.single(Fill(color.copy(alpha = 0.1f)))
            )
        ),
        pointSpacing = ChartPointSpacing,
        verticalAxisPosition = Axis.Position.Vertical.Start,
        rangeProvider = createTimeAxisRangeProvider(
            minX = alignedMinTime, maxX = maxX, minY = minY, maxY = maxY
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
        rangeProvider = createTimeAxisRangeProvider(minX = alignedMinTime, maxX = maxX, minY = minY, maxY = maxY)
    )

    val tickComponent = rememberShapeComponent(
        fill = Fill(MaterialTheme.colorScheme.surface),
        shape = CircleShape,
        strokeFill = Fill(color),
        strokeThickness = 1.5.dp
    )
    val tickLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(
            LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(Color.Transparent)),
                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.dp),
                pointProvider = remember(tickComponent) { TickProvider(tickComponent) }
            )
        ),
        pointSpacing = ChartPointSpacing,
        verticalAxisPosition = Axis.Position.Vertical.Start,
        rangeProvider = createTimeAxisRangeProvider(minX = alignedMinTime, maxX = maxX, minY = minY, maxY = maxY)
    )

    // Layer order must match the model's lineSeries order above.
    val chart = rememberCartesianChart(
        *listOfNotNull(
            curveLayer,
            tickLayer.takeIf { tickEvents.isNotEmpty() },
            bubbleLayer.takeIf { recentEvents.isNotEmpty() }
        ).toTypedArray(),
        startAxis = VerticalAxis.rememberStart(
            label = null,
            tick = null,
            itemPlacer = remember { VerticalAxis.ItemPlacer.count({ 2 }) },
            line = null,
            guideline = null,
            size = SharedStartAxisSize
        ),
        bottomAxis = rememberTimeBottomAxis(showLabels = showTimeLabels, showLine = showTimeLabels),
        layerPadding = { LineChartLayerPadding },
        decorations = listOfNotNull(
            run {
                val rings = recentEvents.mapIndexedNotNull { i, bubble ->
                    val caught = bubble.members.any { m ->
                        m.time in highlightEventTimes || (highlightTime != null && abs(m.time - highlightTime) <= HIGHLIGHT_NEAR_MS)
                    }
                    if (caught) Triple(bubble.time, bubbleYs[i], bubble.diameterDp) else null
                }
                if (rings.isEmpty()) null
                else BubbleHighlightDecoration(rings, minY, maxY, color, MaterialTheme.colorScheme.background, density)
            },
            NowDecoration(
                nowX = realTime.toDouble(),
                lineColor = onSurface.copy(alpha = 0.7f),
                washColor = Color.Transparent,
                drawLine = false
            )
        ),
        getXStep = { _ -> getTimeAxisXStep() },
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        zoomState = zoomState,
        scrollState = scrollState
    )
}
