package uk.scimone.diafit.home.presentation.components

import android.net.Uri
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val durationMinutes: Int = 0,
    val imageUri: Uri? = null
)

/**
 * One panel per therapy type (insulin, carbs), same design for both:
 *  - the summed **activity curve** as a soft filled area (its shape matters, not its absolute
 *    numbers, so the y labels are hidden), extended into the future to show the tail;
 *  - each **event** as a bubble *sitting on the zero line* with area ∝ value and a "6.8 U" /
 *    "45 g" label above it.
 *
 * Bubbles are a second [LineCartesianLayer] on the (axis-less) end axis with a fixed 0..1 range at
 * y = 0. Vico's point provider / data label only see `y`, so the value is encoded as a negligible
 * offset (`value * VALUE_SCALE`) and decoded again. Vico centres points on their y, so
 * [BottomAnchoredCircle] draws each bubble *above* the point (bottom edge on the zero line); to keep
 * the label clear of the bubble the reported point size is 2x the drawn diameter.
 */
private const val VALUE_SCALE = 1e-4

/** Meal-photo size (dp) in the top lane. */
private const val PHOTO_LANE_DIAMETER_DP = 44f

private fun decodeValue(y: Double): Double = (y / VALUE_SCALE).coerceAtLeast(0.0)

/** Draws a circle whose bottom sits at the centre y of the rect Vico hands us (rect = 2x diameter). */
private class BottomAnchoredCircle(private val delegate: Component) : Component {
    override fun draw(context: DrawingContext, left: Float, top: Float, right: Float, bottom: Float) {
        val d = (right - left) / 2f
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        delegate.draw(context, cx - d / 2f, cy - d, cx + d / 2f, cy)
    }
}

/**
 * Meal photos live in their own lane along the top of the panel (above the curve and bubbles), so
 * they never cover the graph. Each is laid out left to right without overlapping its neighbour; a thin
 * stem connects it to its meal's true time, and a photo pushed aside by a neighbour keeps its stem.
 */
private class MealPhotoDecoration(
    private val photos: List<Pair<Long, ImageBitmap>>,
    private val ring: Color,
    private val density: Float
) : Decoration {
    override fun drawOverLayers(context: CartesianDrawingContext) {
        if (photos.isEmpty()) return
        val bounds = context.layerBounds
        val dims = context.layerDimensions
        val ranges = context.ranges
        val d = PHOTO_LANE_DIAMETER_DP * density
        val gap = 3f * density
        val top = bounds.top + 2f * density
        val canvas = context.canvas
        var prevRight = Float.NEGATIVE_INFINITY
        for ((t, photo) in photos.sortedBy { it.first }) {
            val cx = bounds.left + dims.startPadding +
                dims.xSpacing * ((t - ranges.minX) / ranges.xStep).toFloat() - context.scroll
            val left = maxOf(cx - d / 2f, prevRight + gap)
            prevRight = left + d
            if (prevRight < bounds.left || left > bounds.right) continue
            val dst = Rect(left, top, left + d, top + d)
            // stem from the photo down to the meal's time on the zero line
            canvas.drawLine(
                Offset(cx, dst.bottom), Offset(cx, bounds.bottom),
                Paint().apply { color = ring.copy(alpha = 0.8f); style = PaintingStyle.Stroke; strokeWidth = 2f * density }
            )
            val side = minOf(photo.width, photo.height)
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(RoundRect(dst, CornerRadius(8f * density))) }, ClipOp.Intersect)
            canvas.drawImageRect(
                image = photo,
                srcOffset = IntOffset((photo.width - side) / 2, (photo.height - side) / 2),
                srcSize = IntSize(side, side),
                dstOffset = IntOffset(dst.left.toInt(), dst.top.toInt()),
                dstSize = IntSize(dst.width.toInt(), dst.height.toInt()),
                paint = Paint()
            )
            canvas.restore()
            canvas.drawRoundRect(
                dst.left, dst.top, dst.right, dst.bottom, 8f * density, 8f * density,
                Paint().apply { this.color = ring; style = PaintingStyle.Stroke; strokeWidth = 2f * density; isAntiAlias = true }
            )
        }
    }

    override fun equals(other: Any?) =
        other is MealPhotoDecoration && other.ring == ring && other.density == density &&
            other.photos.map { it.first } == photos.map { it.first } &&
            other.photos.map { System.identityHashCode(it.second) } == photos.map { System.identityHashCode(it.second) }

    override fun hashCode() = 31 * photos.map { it.first }.hashCode() + ring.hashCode()
}

private class EventBubbleProvider(
    private val component: Component,
    private val refValue: Double
) : LineCartesianLayer.PointProvider {
    private fun diameter(value: Double): Dp = (10 + 6 * sqrt(value / refValue)).coerceIn(10.0, 30.0).dp

    override fun getPoint(entry: LineCartesianLayerModel.Entry, extraStore: ExtraStore): LineCartesianLayer.Point =
        LineCartesianLayer.Point(component, diameter(decodeValue(entry.y)) * 2)

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
    val recentEvents = events.filter { it.time in alignedMinTime..realTime }

    // Meal photos, decoded small off the main thread, keyed by event time (= Vico entry x).
    val context = LocalContext.current
    val photoUris = recentEvents.mapNotNull { e -> e.imageUri?.let { e.time to it } }
    val photos by produceState(emptyMap<Long, ImageBitmap>(), photoUris) {
        value = withContext(Dispatchers.IO) {
            photoUris.mapNotNull { (t, uri) ->
                runCatching {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                        ?.asImageBitmap()?.let { t to it }
                }.getOrNull()
            }.toMap()
        }
    }

    LaunchedEffect(activityPoints, recentEvents) {
        modelProducer.runTransaction {
            lineSeries {
                series(x = activityPoints.map { it.first }, y = activityPoints.map { it.second })
            }
            if (recentEvents.isNotEmpty()) {
                lineSeries {
                    series(x = recentEvents.map { it.time }, y = recentEvents.map { it.value * VALUE_SCALE })
                }
            }
        }
    }

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
        rangeProvider = createTimeAxisRangeProvider(
            minX = alignedMinTime, maxX = maxX, minY = 0.0, maxY = maxActivity * (if (photos.isEmpty()) 1.15 else 2.5)
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
                pointProvider = remember(bubbleComponent, bubbleRefValue) {
                    EventBubbleProvider(BottomAnchoredCircle(bubbleComponent), bubbleRefValue)
                },
                dataLabel = rememberTextComponent(style = labelStyle),
                dataLabelPosition = Position.Vertical.Top,
                dataLabelValueFormatter = remember(valueUnit) {
                    CartesianValueFormatter { _, value, _ ->
                        val v = Math.round(decodeValue(value) * 10) / 10.0
                        (if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()) + " " + valueUnit
                    }
                }
            )
        ),
        pointSpacing = ChartPointSpacing,
        verticalAxisPosition = Axis.Position.Vertical.End,
        rangeProvider = createTimeAxisRangeProvider(minX = alignedMinTime, maxX = maxX, minY = 0.0, maxY = 1.0)
    )

    val density = LocalDensity.current.density
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
            MealPhotoDecoration(photos.map { (t, bmp) -> t to bmp }, color, density).takeIf { photos.isNotEmpty() },
            selectedTime?.let { SelectionDecoration(it.toDouble(), onSurface.copy(alpha = 0.9f)) }
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
