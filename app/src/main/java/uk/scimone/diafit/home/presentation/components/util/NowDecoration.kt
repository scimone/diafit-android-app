package uk.scimone.diafit.home.presentation.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.PathEffect
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration

/**
 * Marks "now" on a time chart: a dashed vertical line, and a translucent wash over everything to its
 * right so the future (forecast / activity tail) reads as weaker than the measured past. Drawn over
 * the layers; the x -> pixel mapping uses the layer dimensions, scroll and x range, so it follows zoom and scroll.
 */
class NowDecoration(
    private val nowX: Double,
    private val lineColor: Color,
    private val washColor: Color,
    private val dashPx: Float = 10f,
    private val strokePx: Float = 2f,
    /** Draw the dashed line inside this chart. Off when a screen-wide overlay draws it instead. */
    private val drawLine: Boolean = true
) : Decoration {
    override fun drawOverLayers(context: CartesianDrawingContext) {
        val bounds = context.layerBounds
        val dims = context.layerDimensions
        val ranges = context.ranges
        val x = bounds.left + dims.startPadding +
            dims.xSpacing * ((nowX - ranges.minX) / ranges.xStep).toFloat() - context.scroll
        if (x > bounds.right) return

        val canvas = context.canvas
        if (washColor.alpha > 0f) {
            canvas.drawRect(
                x.coerceAtLeast(bounds.left), bounds.top, bounds.right, bounds.bottom,
                Paint().apply { color = washColor; style = PaintingStyle.Fill }
            )
        }
        if (drawLine && x >= bounds.left) {
            canvas.drawLine(
                Offset(x, bounds.top), Offset(x, bounds.bottom),
                Paint().apply {
                    color = lineColor
                    style = PaintingStyle.Stroke
                    strokeWidth = strokePx
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx))
                }
            )
        }
    }

    // Decorations are compared by the chart to decide whether to redraw.
    override fun equals(other: Any?) =
        other is NowDecoration && other.nowX == nowX && other.lineColor == lineColor && other.washColor == washColor

    override fun hashCode() = 31 * (31 * nowX.hashCode() + lineColor.hashCode()) + washColor.hashCode()
}

/**
 * Where a chart's time axis currently sits on screen (after scroll and zoom), in the chart's pixel
 * coordinates. Lets Compose overlays (cursor, meal pins, visible-range filter) line up with the
 * charts without going through Vico's marker system.
 */
data class ChartGeometry(
    val minX: Double,
    /** Pixel x of [minX]. */
    val originPx: Float,
    val pxPerMs: Float,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float
) {
    fun xOf(time: Long): Float = originPx + ((time - minX) * pxPerMs).toFloat()
    fun timeAt(x: Float): Long = (minX + (x - originPx) / pxPerMs).toLong()
    val visibleStart: Long get() = timeAt(left)
    val visibleEnd: Long get() = timeAt(right)
}

/** Draws nothing; reports the chart's [ChartGeometry] on every frame (scroll and zoom change it). */
class GeometryProbe(private val onGeometry: (ChartGeometry) -> Unit) : Decoration {
    override fun drawOverLayers(context: CartesianDrawingContext) {
        val bounds = context.layerBounds
        val dims = context.layerDimensions
        val ranges = context.ranges
        onGeometry(
            ChartGeometry(
                minX = ranges.minX,
                originPx = bounds.left + dims.startPadding - context.scroll,
                pxPerMs = (dims.xSpacing / ranges.xStep).toFloat(),
                left = bounds.left,
                right = bounds.right,
                top = bounds.top,
                bottom = bounds.bottom
            )
        )
    }

    // Stateless apart from the callback, so any two probes are interchangeable for redraw purposes.
    override fun equals(other: Any?) = other is GeometryProbe
    override fun hashCode() = GeometryProbe::class.hashCode()
}


/** Dark band behind the CGM data marking the glucose target range ([lower]..[upper] on a [minY]..[maxY] axis). */
class TargetRangeDecoration(
    private val lower: Double,
    private val upper: Double,
    private val minY: Double,
    private val maxY: Double,
    private val color: Color
) : Decoration {
    override fun drawUnderLayers(context: CartesianDrawingContext) {
        val bounds = context.layerBounds
        val span = (maxY - minY).toFloat()
        fun yOf(v: Double) = bounds.bottom - ((v - minY).toFloat() / span) * bounds.height
        context.canvas.drawRect(
            bounds.left, yOf(upper.coerceAtMost(maxY)), bounds.right, yOf(lower.coerceAtLeast(minY)),
            Paint().apply { this.color = this@TargetRangeDecoration.color; style = PaintingStyle.Fill }
        )
    }

    override fun equals(other: Any?) =
        other is TargetRangeDecoration && other.lower == lower && other.upper == upper &&
            other.minY == minY && other.maxY == maxY && other.color == color

    override fun hashCode() = listOf(lower, upper, minY, maxY, color).hashCode()
}
