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
    private val strokePx: Float = 2f
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
        if (x >= bounds.left) {
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
 * The scrub cursor: a solid vertical line at [selectedX] spanning the chart's whole height. Every
 * stacked chart draws one, so together they read as a single line over all panels.
 */
class SelectionDecoration(
    private val selectedX: Double,
    private val lineColor: Color,
    private val strokePx: Float = 3f
) : Decoration {
    override fun drawOverLayers(context: CartesianDrawingContext) {
        val bounds = context.layerBounds
        val dims = context.layerDimensions
        val ranges = context.ranges
        val x = bounds.left + dims.startPadding +
            dims.xSpacing * ((selectedX - ranges.minX) / ranges.xStep).toFloat() - context.scroll
        if (x < bounds.left || x > bounds.right) return
        context.canvas.drawLine(
            Offset(x, bounds.top), Offset(x, bounds.bottom),
            Paint().apply { color = lineColor; style = PaintingStyle.Stroke; strokeWidth = strokePx }
        )
    }

    override fun equals(other: Any?) =
        other is SelectionDecoration && other.selectedX == selectedX && other.lineColor == lineColor

    override fun hashCode() = 31 * selectedX.hashCode() + lineColor.hashCode()
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
