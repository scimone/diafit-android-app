package uk.scimone.diafit.home.presentation.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import uk.scimone.diafit.patterns.domain.PatternAgp
import java.util.Calendar

/**
 * The 24 h ambulatory glucose profile drawn behind the CGM curve: for every day the chart shows, the
 * typical middle half (25-75th percentile) and the wider 10-90th band, with the median as a thin line.
 * Beyond "now" it simply continues, so it also reads as what is usually to come.
 */
class AgpDecoration(
    private val agp: PatternAgp,
    private val minY: Double,
    private val maxY: Double,
    private val color: Color
) : Decoration {
    override fun drawUnderLayers(context: CartesianDrawingContext) {
        val bounds = context.layerBounds
        val dims = context.layerDimensions
        val ranges = context.ranges
        val span = (maxY - minY).toFloat()
        val bins = PatternAgp.BINS
        val binMs = PatternAgp.BIN_MINUTES * 60_000L

        fun xOf(time: Long) = bounds.left + dims.startPadding +
            dims.xSpacing * ((time - ranges.minX) / ranges.xStep).toFloat() - context.scroll
        fun yOf(v: Double) = bounds.bottom - ((v.coerceIn(minY, maxY) - minY).toFloat() / span) * bounds.height

        // Visible time range, a little wider so the bands run off the edges.
        val pxPerMs = (dims.xSpacing / ranges.xStep).toFloat()
        val firstTime = ranges.minX.toLong() + ((bounds.left - xOf(ranges.minX.toLong())) / pxPerMs).toLong() - binMs
        val lastTime = ranges.minX.toLong() + ((bounds.right - xOf(ranges.minX.toLong())) / pxPerMs).toLong() + binMs

        val day = Calendar.getInstance().apply {
            timeInMillis = firstTime
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val times = ArrayList<Long>()
        val bin = ArrayList<Int>()
        while (day.timeInMillis <= lastTime) {
            val start = day.timeInMillis
            for (b in 0 until bins) {
                val t = start + b * binMs
                if (t in firstTime..lastTime) { times += t; bin += b }
            }
            day.add(Calendar.DAY_OF_YEAR, 1)
        }
        if (times.size < 2) return

        fun band(lower: DoubleArray, upper: DoubleArray): Path = Path().apply {
            times.indices.forEach { i ->
                val x = xOf(times[i]); val y = yOf(upper[bin[i]])
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            for (i in times.indices.reversed()) lineTo(xOf(times[i]), yOf(lower[bin[i]]))
            close()
        }
        val canvas = context.canvas
        val tint = color
        canvas.save()
        canvas.clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        fun fill(path: Path, alpha: Float) = canvas.drawPath(
            path, Paint().apply { this.color = tint.copy(alpha = alpha); style = PaintingStyle.Fill; isAntiAlias = true }
        )
        fill(band(agp.p10, agp.p90), 0.16f)
        fill(band(agp.p25, agp.p75), 0.26f)

        val median = Path().apply {
            times.indices.forEach { i ->
                val x = xOf(times[i]); val y = yOf(agp.p50[bin[i]])
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        canvas.drawPath(
            median,
            Paint().apply {
                this.color = tint.copy(alpha = 0.7f); style = PaintingStyle.Stroke
                strokeWidth = 3f; strokeCap = StrokeCap.Round; isAntiAlias = true
            }
        )
        canvas.restore()
    }

    override fun equals(other: Any?) =
        other is AgpDecoration && other.agp === agp && other.minY == minY && other.maxY == maxY && other.color == color

    override fun hashCode() = listOf(System.identityHashCode(agp), minY, maxY, color).hashCode()
}
