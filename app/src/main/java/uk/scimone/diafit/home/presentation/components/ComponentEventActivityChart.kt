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

/** Meal-photo bubble diameter (dp): big enough to recognise the food at a glance. */
private const val PHOTO_DIAMETER = 52.0

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
 * A meal photo as a bubble: the picture cropped to a circle (bottom on the zero line like the plain
 * bubbles) with a ring in the panel colour, so it still reads as "carbs" while showing the food.
 */
private class PhotoBubble(
    private val photo: ImageBitmap,
    private val ring: Color
) : Component {
    override fun draw(context: DrawingContext, left: Float, top: Float, right: Float, bottom: Float) {
        val d = (right - left) / 2f
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val dst = Rect(cx - d / 2f, cy - d, cx + d / 2f, cy)
        val side = minOf(photo.width, photo.height)
        val canvas = context.canvas
        canvas.save()
        canvas.clipPath(Path().apply { addOval(dst) }, ClipOp.Intersect)
        canvas.drawImageRect(
            image = photo,
            srcOffset = IntOffset((photo.width - side) / 2, (photo.height - side) / 2),
            srcSize = IntSize(side, side),
            dstOffset = IntOffset(dst.left.toInt(), dst.top.toInt()),
            dstSize = IntSize(dst.width.toInt(), dst.height.toInt()),
            paint = Paint()
        )
        canvas.restore()
        canvas.drawCircle(
            dst.center, d / 2f,
            Paint().apply { color = ring; style = PaintingStyle.Stroke; strokeWidth = 3f; isAntiAlias = true }
        )
    }
}

private class EventBubbleProvider(
    private val component: Component,
    private val refValue: Double,
    private val photos: Map<Long, ImageBitmap>,
    private val ring: Color
) : LineCartesianLayer.PointProvider {
    // Photo bubbles get a larger floor so the food is recognisable.
    private fun diameter(value: Double, hasPhoto: Boolean): Dp {
        val d = (10 + 6 * sqrt(value / refValue)).coerceIn(10.0, 30.0)
        return (if (hasPhoto) PHOTO_DIAMETER else d).dp
    }

    override fun getPoint(entry: LineCartesianLayerModel.Entry, extraStore: ExtraStore): LineCartesianLayer.Point {
        val photo = photos[entry.x.toLong()]
        val comp = if (photo != null) BottomAnchoredPhoto(PhotoBubble(photo, ring)) else component
        return LineCartesianLayer.Point(comp, diameter(decodeValue(entry.y), photo != null) * 2)
    }

    // Deliberately constant: Vico pads each layer by half its largest point, and every Home chart
    // must end up with identical padding to stay x-aligned.
    override fun getLargestPoint(extraStore: ExtraStore) = LineCartesianLayer.Point(component, ChartPointSize)
}

/** PhotoBubble already anchors itself to the zero line, so it is used as-is. */
private fun BottomAnchoredPhoto(c: PhotoBubble): Component = c

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
    }.toList()

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
            minX = alignedMinTime, maxX = maxX, minY = 0.0, maxY = maxActivity * 1.15
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
                pointProvider = remember(bubbleComponent, bubbleRefValue, photos, color) {
                    EventBubbleProvider(BottomAnchoredCircle(bubbleComponent), bubbleRefValue, photos, color)
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
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        zoomState = zoomState,
        scrollState = scrollState
    )
}
