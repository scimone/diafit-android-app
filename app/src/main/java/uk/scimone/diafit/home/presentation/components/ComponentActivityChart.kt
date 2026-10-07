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
private const val HR_DEFAULT_MAX = 150
/** A heart-rate gap longer than this breaks the line instead of bridging missing data. */
private const val HR_GAP_MS = 8 * 60_000L
/** The faint reference line (and its label) in the heart-rate plot. */
private const val HR_REFERENCE = 100
/** A 15-minute step bucket this full reaches the maximum bar height (brisk walking, ~80 steps/min). */
private const val STEPS_FULL = 1200
private const val STEPS_BAR_FRACTION = 0.3f
/** Exercise intensity colouring is relative to this heart-rate span. */
private const val INTENSITY_LOW_BPM = 85
private const val INTENSITY_HIGH_BPM = 150
private const val EXERCISE_SLICE_MS = 3 * 60_000L

private val LaneHeight = 18.dp
private val LaneGap = 3.dp
private val PlotTopInset = 4.dp

/** An exercise session cut into slices, each with the intensity (0..1) its heart rate showed. */
private class ExerciseDrawing(val exercise: ExerciseEntity, val slices: List<Triple<Long, Long, Float>>)

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
 * it pans, zooms and aligns exactly with the Vico panels without being a Vico chart itself:
 *  - **heart rate** as a line (gaps in the data break it) in the upper part, with a faint 100 bpm guide;
 *  - **steps** as soft bars along the bottom of that area;
 *  - **sleep** as a tinted wash behind the heart rate plus a hypnogram (awake / REM / light / deep rows)
 *    in the lane underneath, labelled with its length;
 *  - **exercise** as a bar in the same lane, shaded by the intensity the heart rate showed, labelled
 *    with its type and length, plus a faint wash behind the heart rate so the spikes line up with it.
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
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val guide = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val heartTimes = remember(data.heartRate) { LongArray(data.heartRate.size) { data.heartRate[it].timestamp } }
    val heartBpm = remember(data.heartRate) { IntArray(data.heartRate.size) { data.heartRate[it].bpm } }
    val hrMax = remember(data.heartRate) {
        val peak = data.heartRate.maxOfOrNull { it.bpm } ?: HR_DEFAULT_MAX
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
            ExerciseDrawing(ex, slices)
        }
    }

    Canvas(modifier) {
        val g = geometry.value ?: return@Canvas
        val nowX = g.xOf(window.now)
        val clipRight = min(g.right, nowX)
        if (clipRight <= g.left) return@Canvas

        val laneH = LaneHeight.toPx()
        val laneTop = size.height - laneH
        val plotTop = PlotTopInset.toPx()
        val plotBottom = laneTop - LaneGap.toPx()

        clipRect(left = g.left, top = 0f, right = clipRight, bottom = size.height) {
            // Lane track.
            drawRoundRect(track, Offset(g.left, laneTop), Size(clipRight - g.left, laneH), CornerRadius(4.dp.toPx()))

            // Sleep: wash behind the plot.
            sessions.forEach { s ->
                val x0 = g.xOf(s.startUtc); val x1 = g.xOf(s.endUtc)
                if (x1 >= g.left && x0 <= clipRight) {
                    drawRect(Sleep.copy(alpha = 0.10f), Offset(x0, 0f), Size(x1 - x0, plotBottom + LaneGap.toPx() / 2))
                }
            }
            // Exercise: faint wash so the lane block and the heart-rate spikes read as one thing.
            exercises.forEach { e ->
                val x0 = g.xOf(e.exercise.startUtc); val x1 = g.xOf(e.exercise.endUtc)
                if (x1 >= g.left && x0 <= clipRight) {
                    drawRect(Activity.copy(alpha = 0.09f), Offset(x0, 0f), Size(x1 - x0, plotBottom + LaneGap.toPx() / 2))
                }
            }

            // Reference line.
            val plotHeight = plotBottom - plotTop
            fun yOf(bpm: Int) = plotBottom - ((bpm - HR_MIN).toFloat() / (hrMax - HR_MIN)).coerceIn(0f, 1f) * plotHeight
            val refY = yOf(HR_REFERENCE)
            drawLine(guide, Offset(g.left, refY), Offset(clipRight, refY), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            drawText(
                textMeasurer, "$HR_REFERENCE", Offset(g.left + 3.dp.toPx(), refY - 11.dp.toPx()),
                style = TextStyle(color = labelColor.copy(alpha = 0.7f), fontSize = 9.sp)
            )

            // Steps.
            val barMax = plotHeight * STEPS_BAR_FRACTION
            data.steps.forEach { st ->
                val x0 = g.xOf(st.startUtc); val x1 = g.xOf(st.startUtc + uk.scimone.diafit.core.domain.model.StepsEntity.STEP_BUCKET_MS)
                if (x1 < g.left || x0 > clipRight) return@forEach
                val h = barMax * min(1f, st.count / STEPS_FULL.toFloat()).coerceAtLeast(0.08f)
                drawRect(Activity.copy(alpha = 0.28f), Offset(x0 + 0.5f, plotBottom - h), Size(max(1f, x1 - x0 - 1f), h))
            }

            // Heart rate.
            if (heartTimes.isNotEmpty()) {
                val first = max(0, lowerBound(heartTimes, g.timeAt(g.left - 40f)) - 1)
                val last = min(heartTimes.size - 1, lowerBound(heartTimes, g.timeAt(clipRight + 40f)))
                val path = Path()
                var prevTime = Long.MIN_VALUE
                for (i in first..last) {
                    val x = g.xOf(heartTimes[i]); val y = yOf(heartBpm[i])
                    if (prevTime == Long.MIN_VALUE || heartTimes[i] - prevTime > HR_GAP_MS) path.moveTo(x, y) else path.lineTo(x, y)
                    prevTime = heartTimes[i]
                }
                drawPath(path, Activity, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // Sleep: hypnogram in the lane + label.
            val rowH = laneH / 4f
            sessions.forEach { s -> drawSleepLane(s, g, laneTop, rowH, plotTop, textMeasurer) }
            // Exercise: intensity-shaded bar in the lane + label.
            exercises.forEach { drawExerciseLane(it, g, laneTop, laneH, textMeasurer) }
        }
    }
}

private fun DrawScope.drawSleepLane(s: SleepSession, g: ChartGeometry, laneTop: Float, rowH: Float, labelTop: Float, textMeasurer: TextMeasurer) {
    val corner = CornerRadius(1.5.dp.toPx())
    s.stages.forEach { st ->
        val x0 = g.xOf(st.startUtc); val x1 = g.xOf(st.endUtc)
        if (x1 < g.left || x0 > g.right) return@forEach
        val stage = st.sleepStage
        val row = stageRow(stage)
        // Sleep without stage detail is one flat bar through the middle rows.
        val top = laneTop + row * rowH + 0.5f
        val height = if (stage == SleepStage.SLEEPING) rowH * 2 - 1f else rowH - 1f
        drawRoundRect(stageColor(stage), Offset(x0, top), Size(max(1.5f, x1 - x0), height), corner)
    }
    val x0 = max(g.xOf(s.startUtc), g.left)
    val label = "Sleep ${formatDuration(s.asleepMs)}"
    val layout = textMeasurer.measure(label, TextStyle(color = Sleep, fontSize = 9.sp, fontWeight = FontWeight.SemiBold))
    if (g.xOf(s.endUtc) - x0 > layout.size.width + 8.dp.toPx()) {
        drawText(layout, topLeft = Offset(x0 + 4.dp.toPx(), labelTop))
    }
}

private fun DrawScope.drawExerciseLane(e: ExerciseDrawing, g: ChartGeometry, laneTop: Float, laneH: Float, textMeasurer: TextMeasurer) {
    val x0 = g.xOf(e.exercise.startUtc); val x1 = g.xOf(e.exercise.endUtc)
    if (x1 < g.left || x0 > g.right) return
    val shape = Path().apply {
        addRoundRect(RoundRect(x0, laneTop, max(x1, x0 + 3f), laneTop + laneH, CornerRadius(4.dp.toPx())))
    }
    clipPath(shape) {
        e.slices.forEach { (s, t, intensity) ->
            drawRect(Activity.copy(alpha = 0.4f + 0.6f * intensity), Offset(g.xOf(s), laneTop), Size(max(1f, g.xOf(t) - g.xOf(s) + 1f), laneH))
        }
    }
    val label = "${e.exercise.title ?: "Exercise"} ${formatDuration(e.exercise.durationMs)}"
    val layout = textMeasurer.measure(label, TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
    val visibleX0 = max(x0, g.left)
    if (x1 - visibleX0 > layout.size.width + 8.dp.toPx()) {
        drawText(layout, topLeft = Offset(visibleX0 + 5.dp.toPx(), laneTop + (laneH - layout.size.height) / 2))
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
