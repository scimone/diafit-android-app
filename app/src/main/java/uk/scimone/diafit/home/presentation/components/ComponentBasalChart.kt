package uk.scimone.diafit.home.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.BasalSegment
import uk.scimone.diafit.home.presentation.utils.ChartGeometry
import uk.scimone.diafit.home.presentation.utils.ChartTimeWindow
import uk.scimone.diafit.ui.theme.Basal
import kotlin.math.max
import kotlin.math.min

private val PlotTopInset = 14.dp
private val PlotBottomInset = 3.dp
/** The y axis always spans at least this many U/h, so a tiny basal doesn't fill the panel. */
private const val MIN_AXIS_RATE = 0.5

/** "0.30" style: two decimals below 10 U/h. */
internal fun formatRate(rate: Double): String = "%.2f".format(java.util.Locale.US, rate)

/**
 * The basal panel: what ran (**filled step line**) on the shared time axis, drawn on a Canvas through the CGM
 * chart's [geometry]. Where the loop's temp basal differs from the profile's scheduled rate, the schedule is
 * shown as a dashed line. Nothing is drawn after [ChartTimeWindow.now].
 */
@Composable
fun ComponentBasalChart(
    segments: List<BasalSegment>,
    window: ChartTimeWindow,
    geometry: State<ChartGeometry?>,
    modifier: Modifier = Modifier
) {
    val axisMax = remember(segments) {
        max(MIN_AXIS_RATE, (segments.maxOfOrNull { max(it.delivered, it.scheduled ?: 0.0) } ?: 0.0) * 1.2)
    }

    Canvas(modifier) {
        val g = geometry.value ?: return@Canvas
        val clipRight = min(g.right, g.xOf(window.now))
        if (clipRight <= g.left || segments.isEmpty()) return@Canvas

        val plotTop = PlotTopInset.toPx()
        val plotBottom = size.height - PlotBottomInset.toPx()
        fun yOf(rate: Double) = plotBottom - (rate / axisMax).toFloat().coerceIn(0f, 1f) * (plotBottom - plotTop)

        clipRect(left = g.left, top = 0f, right = clipRight, bottom = size.height) {
            // Delivered rate: one closed area per run of touching segments, with the step line on top.
            var line: Path? = null
            var area: Path? = null
            var runEnd = Long.MIN_VALUE
            var runStartX = 0f
            var lastX = 0f
            fun finishRun() {
                val l = line ?: return
                val a = area ?: return
                a.lineTo(lastX, plotBottom); a.lineTo(runStartX, plotBottom); a.close()
                drawPath(a, Basal.copy(alpha = 0.22f))
                drawPath(l, Basal, style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
                line = null; area = null
            }
            segments.forEach { s ->
                val x0 = g.xOf(s.startUtc); val x1 = g.xOf(s.endUtc)
                if (x1 < g.left - 4f || x0 > clipRight + 4f) { return@forEach }
                val y = yOf(s.delivered)
                if (line == null || s.startUtc != runEnd) {
                    finishRun()
                    line = Path().apply { moveTo(x0, y) }
                    area = Path().apply { moveTo(x0, plotBottom); lineTo(x0, y) }
                    runStartX = x0
                } else {
                    line!!.lineTo(x0, y); area!!.lineTo(x0, y)
                }
                line!!.lineTo(x1, y); area!!.lineTo(x1, y)
                lastX = x1
                runEnd = s.endUtc
            }
            finishRun()

            // Scheduled rate, only where the delivered one differs.
            val dash = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))
            segments.forEach { s ->
                val sched = s.scheduled ?: return@forEach
                if (kotlin.math.abs(sched - s.delivered) < 1e-6) return@forEach
                val y = yOf(sched)
                val p = Path().apply { moveTo(g.xOf(s.startUtc), y); lineTo(g.xOf(s.endUtc), y) }
                drawPath(p, Basal, style = dash)
            }
        }
    }
}
