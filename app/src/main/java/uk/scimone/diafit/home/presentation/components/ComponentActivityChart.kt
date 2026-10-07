package uk.scimone.diafit.home.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
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
import uk.scimone.diafit.core.domain.model.ExerciseEntity
import uk.scimone.diafit.core.domain.model.SleepSession
import uk.scimone.diafit.core.domain.model.SleepStage
import uk.scimone.diafit.core.domain.model.StepsEntity
import uk.scimone.diafit.home.presentation.utils.ChartGeometry
import uk.scimone.diafit.home.presentation.utils.ChartTimeWindow
import uk.scimone.diafit.ui.theme.Activity
import uk.scimone.diafit.ui.theme.Sleep
import uk.scimone.diafit.ui.theme.SleepAwake
import uk.scimone.diafit.ui.theme.SleepDeep
import uk.scimone.diafit.ui.theme.SleepLight
import uk.scimone.diafit.ui.theme.SleepRem
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
/** Exercise intensity colouring is relative to this heart-rate span. */
private const val INTENSITY_LOW_BPM = 85
private const val INTENSITY_HIGH_BPM = 150
private const val EXERCISE_SLICE_MS = 3 * 60_000L

/** Space under the panel title, then the heart-rate plot, then the sleep/exercise lane. */
private val PlotTopInset = 12.dp
private val PlotMaxHeight = 40.dp
private val LaneLabelHeight = 11.dp
private val LaneBlockHeight = 20.dp
private val LaneHeight = LaneLabelHeight + LaneBlockHeight + 2.dp
private val LaneGap = 4.dp

/** An exercise session cut into slices, each with the intensity (0..1) its heart rate showed. */
private class ExerciseDrawing(val exercise: ExerciseEntity, val slices: List<Triple<Long, Long, Float>>, val avgBpm: Int?)

private fun stageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.AWAKE -> SleepAwake
    SleepStage.REM -> SleepRem
    SleepStage.LIGHT -> SleepLight
    SleepStage.DEEP -> SleepDeep
    SleepStage.SLEEPING -> Sleep
}

/** Row of a stage in the hypnogram, top (awake) to bottom (deep). */
private fun stageRow(stage: SleepStage): Int = when (stage) {
    SleepStage.AWAKE -> 0
    SleepStage.REM -> 1
    SleepStage.LIGHT -> 2
    SleepStage.DEEP -> 3
    SleepStage.SLEEPING -> 1
}

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
 *  - a **sleep / exercise lane**, each item a labelled capsule: sleep as a hypnogram (awake / REM /
 *    light / deep rows, so the night's shape is visible), exercise as an intensity profile (one bar per
 *    few minutes, as tall as the heart rate was high) with its type, length and average heart rate.
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
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)

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
    val exercises = remember(data.exercise, data.heartRate) {
        data.exercise.map { ex ->
            val slices = generateSequence(ex.startUtc) { it + EXERCISE_SLICE_MS }.takeWhile { it < ex.endUtc }.map { s ->
                val e = min(s + EXERCISE_SLICE_MS, ex.endUtc)
                val from = lowerBound(heartTimes, s)
                val to = lowerBound(heartTimes, e)
                val intensity = if (to > from) {
                    var sum = 0
                    for (i in from until to) sum += heartBpm[i]
                    ((sum / (to - from) - INTENSITY_LOW_BPM).toFloat() / (INTENSITY_HIGH_BPM - INTENSITY_LOW_BPM)).coerceIn(0f, 1f)
                } else 0.5f
                Triple(s, e, intensity)
            }.toList()
            val during = lowerBound(heartTimes, ex.startUtc) until lowerBound(heartTimes, ex.endUtc)
            ExerciseDrawing(ex, slices, if (during.isEmpty()) null else during.sumOf { heartBpm[it] } / during.count())
        }
    }

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
            sessions.forEach { s -> drawSleepCapsule(s, g, laneTop, track, textMeasurer) }
            exercises.forEach { drawExerciseCapsule(it, g, laneTop, track, textMeasurer) }
        }
    }
}

/** Sleep as a capsule holding a hypnogram: awake on top, then REM, light, deep at the bottom. */
private fun DrawScope.drawSleepCapsule(s: SleepSession, g: ChartGeometry, laneTop: Float, track: Color, textMeasurer: TextMeasurer) {
    val x0 = g.xOf(s.startUtc); val x1 = g.xOf(s.endUtc)
    if (x1 < g.left || x0 > g.right) return
    val labelH = LaneLabelHeight.toPx()
    val blockH = LaneBlockHeight.toPx()
    val capsule = RoundRect(x0, laneTop + labelH, max(x1, x0 + 3f), laneTop + labelH + blockH, CornerRadius(5.dp.toPx()))
    drawPath(Path().apply { addRoundRect(capsule) }, Sleep.copy(alpha = 0.14f))
    val rowH = (blockH - 4.dp.toPx()) / 4f
    val inset = 2.dp.toPx()
    clipPath(Path().apply { addRoundRect(capsule) }) {
        s.stages.forEach { st ->
            val sx0 = g.xOf(st.startUtc); val sx1 = g.xOf(st.endUtc)
            if (sx1 < g.left || sx0 > g.right) return@forEach
            val stage = st.sleepStage
            val top = laneTop + labelH + inset + stageRow(stage) * rowH
            val h = if (stage == SleepStage.SLEEPING) rowH * 2 else rowH
            drawRoundRect(stageColor(stage), Offset(sx0, top + 0.5f), Size(max(1.5f, sx1 - sx0), h - 1f), CornerRadius(1.5.dp.toPx()))
        }
    }
    drawCapsuleLabel("Sleep ${formatDuration(s.asleepMs)}", Sleep, maxOf(x0, g.left), x1, laneTop, labelH, textMeasurer)
}

/** Exercise as a capsule holding an intensity profile: a bar per few minutes, as tall as the heart rate was high. */
private fun DrawScope.drawExerciseCapsule(e: ExerciseDrawing, g: ChartGeometry, laneTop: Float, track: Color, textMeasurer: TextMeasurer) {
    val x0 = g.xOf(e.exercise.startUtc); val x1 = g.xOf(e.exercise.endUtc)
    if (x1 < g.left || x0 > g.right) return
    val labelH = LaneLabelHeight.toPx()
    val blockH = LaneBlockHeight.toPx()
    val capsule = RoundRect(x0, laneTop + labelH, max(x1, x0 + 3f), laneTop + labelH + blockH, CornerRadius(5.dp.toPx()))
    drawPath(Path().apply { addRoundRect(capsule) }, Activity.copy(alpha = 0.16f))
    val inset = 2.dp.toPx()
    clipPath(Path().apply { addRoundRect(capsule) }) {
        e.slices.forEach { (s, t, intensity) ->
            val h = (blockH - 2 * inset) * (0.25f + 0.75f * intensity)
            val bx0 = g.xOf(s); val bx1 = g.xOf(t)
            drawRect(Activity, Offset(bx0, laneTop + labelH + blockH - inset - h), Size(max(1f, bx1 - bx0 - 0.5f), h))
        }
    }
    val title = e.exercise.title ?: "Exercise"
    val label = "$title ${formatDuration(e.exercise.durationMs)}" + (e.avgBpm?.let { " · $it bpm" } ?: "")
    // Fall back to the short form when the full label doesn't fit above the capsule.
    drawCapsuleLabel(label, Activity, maxOf(x0, g.left), x1, laneTop, labelH, textMeasurer, fallback = "$title ${formatDuration(e.exercise.durationMs)}")
}

private fun DrawScope.drawCapsuleLabel(
    text: String, color: Color, x0: Float, x1: Float, laneTop: Float, labelH: Float, textMeasurer: TextMeasurer, fallback: String? = null
) {
    val style = TextStyle(color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    val room = x1 - x0
    for (candidate in listOfNotNull(text, fallback)) {
        val layout = textMeasurer.measure(candidate, style, maxLines = 1)
        if (room >= layout.size.width + 4.dp.toPx()) {
            drawText(layout, topLeft = Offset(x0 + 2.dp.toPx(), laneTop + (labelH - layout.size.height) / 2))
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
