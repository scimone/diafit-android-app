package uk.scimone.diafit.home.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.SleepStage
import uk.scimone.diafit.core.domain.model.StepsEntity
import uk.scimone.diafit.home.presentation.utils.ChartGeometry
import uk.scimone.diafit.home.presentation.utils.ChartTimeWindow
import uk.scimone.diafit.ui.theme.Activity
import uk.scimone.diafit.ui.theme.Sleep
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Heart-rate axis: [HR_MIN] at the bottom of the plot, [HR_DEFAULT_MAX] (or the day's peak) at the top. */
private const val HR_MIN = 40
private const val HR_DEFAULT_MAX = 170
/** A heart-rate gap longer than this breaks the line instead of bridging missing data. */
private const val HR_GAP_MS = 8 * 60_000L
/** The line is a 5-minute moving mean of the per-minute values: calmer, same shape. */
private const val HR_SMOOTH_POINTS = 5
/** A 15-minute step bucket this full reaches the maximum bar height (brisk walking, ~80 steps/min). */
private const val STEPS_FULL = 1200
private const val STEPS_BAR_FRACTION = 0.45f
/** Space under the panel title, then the heart-rate plot, then the sleep/exercise lane. */
private val PlotTopInset = 12.dp
private val PlotMaxHeight = 40.dp
private val LaneHeight = 16.dp
private val LaneGap = 4.dp

internal fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

/**
 * The activity panel, drawn straight onto a Canvas through the shared [geometry] of the CGM chart so
 * it pans, zooms and aligns exactly with the Vico panels without being a Vico chart itself.
 * Two parts, top to bottom:
 *  - a compact **heart-rate plot** (smoothed line with a soft fill, gaps in the data break it) with
 *    **steps** as faint bars along its bottom;
 *  - a lane with one plain capsule per **sleep** session and per **workout**: just the time frame and
 *    a label (sleep length, or exercise type and length).
 * Nothing is drawn after [ChartTimeWindow.now].
 */
@Composable
fun ComponentActivityChart(
    data: ActivityData,
    window: ChartTimeWindow,
    geometry: State<ChartGeometry?>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    val heartTimes = remember(data.heartRate) { LongArray(data.heartRate.size) { data.heartRate[it].timestamp } }
    val heartBpm = remember(data.heartRate) { IntArray(data.heartRate.size) { data.heartRate[it].bpm } }
    // Moving mean over neighbouring readings, never across a gap in the data.
    val smoothBpm = remember(heartTimes, heartBpm) {
        FloatArray(heartBpm.size) { i ->
            var sum = 0f; var n = 0
            for (j in (i - HR_SMOOTH_POINTS / 2)..(i + HR_SMOOTH_POINTS / 2)) {
                if (j in heartBpm.indices && kotlin.math.abs(heartTimes[j] - heartTimes[i]) <= HR_SMOOTH_POINTS * 60_000L) { sum += heartBpm[j]; n++ }
            }
            sum / n
        }
    }
    val hrMax = remember(smoothBpm) {
        val peak = smoothBpm.maxOrNull()?.toInt() ?: HR_DEFAULT_MAX
        max(HR_DEFAULT_MAX, (ceil(peak / 10.0) * 10).toInt())
    }
    val sessions = remember(data.sleep) { data.sleepSessions }

    Canvas(modifier) {
        val g = geometry.value ?: return@Canvas
        val nowX = g.xOf(window.now)
        val clipRight = min(g.right, nowX)
        if (clipRight <= g.left) return@Canvas

        val laneH = LaneHeight.toPx()
        val laneTop = size.height - laneH
        val plotBottom = laneTop - LaneGap.toPx()
        // The heart-rate plot stays small however tall the panel is; the lane sits right under it.
        val plotTop = max(PlotTopInset.toPx(), plotBottom - PlotMaxHeight.toPx())
        val plotHeight = plotBottom - plotTop
        fun yOf(bpm: Float) = plotBottom - ((bpm - HR_MIN) / (hrMax - HR_MIN)).coerceIn(0f, 1f) * plotHeight

        clipRect(left = g.left, top = 0f, right = clipRight, bottom = size.height) {
            // Steps.
            val barMax = plotHeight * STEPS_BAR_FRACTION
            data.steps.forEach { st ->
                val x0 = g.xOf(st.startUtc); val x1 = g.xOf(st.startUtc + StepsEntity.STEP_BUCKET_MS)
                if (x1 < g.left || x0 > clipRight) return@forEach
                val h = barMax * min(1f, st.count / STEPS_FULL.toFloat()).coerceAtLeast(0.08f)
                drawRect(Activity.copy(alpha = 0.28f), Offset(x0 + 0.5f, plotBottom - h), Size(max(1f, x1 - x0 - 1f), h))
            }

            // Heart rate.
            if (heartTimes.isNotEmpty()) {
                val first = max(0, lowerBound(heartTimes, g.timeAt(g.left - 40f)) - 1)
                val last = min(heartTimes.size - 1, lowerBound(heartTimes, g.timeAt(clipRight + 40f)))
                val line = Path()
                val area = Path()
                var prevTime = Long.MIN_VALUE
                var segmentStartX = 0f
                var prevX = 0f
                fun closeArea() { if (prevTime != Long.MIN_VALUE) { area.lineTo(prevX, plotBottom); area.lineTo(segmentStartX, plotBottom); area.close() } }
                for (i in first..last) {
                    val x = g.xOf(heartTimes[i]); val y = yOf(smoothBpm[i])
                    if (prevTime == Long.MIN_VALUE || heartTimes[i] - prevTime > HR_GAP_MS) {
                        closeArea()
                        line.moveTo(x, y); area.moveTo(x, y); segmentStartX = x
                    } else { line.lineTo(x, y); area.lineTo(x, y) }
                    prevTime = heartTimes[i]; prevX = x
                }
                closeArea()
                drawPath(area, Brush.verticalGradient(listOf(Activity.copy(alpha = 0.22f), Color.Transparent), startY = plotTop, endY = plotBottom))
                drawPath(line, Activity, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // Lane: one capsule per sleep session / workout.
            sessions.forEach { s ->
                drawCapsule(g.xOf(s.startUtc), g.xOf(s.endUtc), g, laneTop, laneH, Sleep, "Sleep ${formatDuration(s.asleepMs)}", textMeasurer)
            }
            data.exercise.forEach { e ->
                val title = e.title ?: "Exercise"
                drawCapsule(g.xOf(e.startUtc), g.xOf(e.endUtc), g, laneTop, laneH, Activity, "$title ${formatDuration(e.durationMs)}", textMeasurer, fallback = title)
            }
        }
    }
}

/** A plain filled capsule over [x0]..[x1]; the label is written inside when it fits (the short [fallback] if only that does). */
private fun DrawScope.drawCapsule(
    x0: Float, x1: Float, g: ChartGeometry, laneTop: Float, laneH: Float, color: Color,
    label: String, textMeasurer: TextMeasurer, fallback: String? = null
) {
    if (x1 < g.left || x0 > g.right) return
    drawRoundRect(color, Offset(x0, laneTop), Size(max(3f, x1 - x0), laneH), CornerRadius(5.dp.toPx()))
    val style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    val textX = max(x0, g.left) + 5.dp.toPx()
    for (candidate in listOfNotNull(label, fallback)) {
        val layout = textMeasurer.measure(candidate, style, maxLines = 1)
        if (x1 - textX >= layout.size.width + 4.dp.toPx()) {
            drawText(layout, topLeft = Offset(textX, laneTop + (laneH - layout.size.height) / 2))
            return
        }
    }
}

/** Index of the first element of [sorted] that is >= [value]. */
internal fun lowerBound(sorted: LongArray, value: Long): Int {
    var lo = 0
    var hi = sorted.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (sorted[mid] < value) lo = mid + 1 else hi = mid
    }
    return lo
}

/** What the inspection readout says about activity at the cursor. */
data class ActivityReadout(val bpm: Int?, val label: String?)

private const val HR_READOUT_SNAP_MS = 10 * 60_000L

/** Heart rate (nearest reading within 10 min) and what the user was doing (exercise or sleep stage) at [time]. */
fun ActivityData.readoutAt(time: Long): ActivityReadout? {
    val bpm = heartRate.minByOrNull { kotlin.math.abs(it.timestamp - time) }
        ?.takeIf { kotlin.math.abs(it.timestamp - time) <= HR_READOUT_SNAP_MS }?.bpm
    val doing = exercise.firstOrNull { time in it.startUtc..it.endUtc }?.let { it.title ?: "Exercise" }
        ?: sleep.firstOrNull { time in it.startUtc until it.endUtc }?.let {
            if (it.sleepStage == SleepStage.SLEEPING) "Asleep" else "Sleep · ${it.sleepStage.label}"
        }
    return if (bpm == null && doing == null) null else ActivityReadout(bpm, doing)
}
