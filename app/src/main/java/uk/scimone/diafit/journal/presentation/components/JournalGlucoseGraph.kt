package uk.scimone.diafit.journal.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.scimone.diafit.journal.presentation.DayGlucose
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.InRange
import uk.scimone.diafit.ui.theme.targetRangeBandColor

/** The graph shows this many minutes, with the marker (the highlighted entry's timestamp) in the middle. */
const val JOURNAL_GRAPH_WINDOW_MIN = 360f

private const val MIN_MS = 60_000L
private const val DAY_MS = 24 * 60 * MIN_MS

/**
 * Glucose around the highlighted journal entry, in the style of Home's glucose panel (target band, dots
 * coloured by range) but fixed at one zoom level. It is drawn directly from the few cached days in
 * [glucose], so scrolling never has to build a chart model. [centerMinutes] is the marker position in minutes
 * after [baseMs]; it is read while drawing only, so moving it redraws without recomposing.
 */
@Composable
fun JournalGlucoseGraph(
    centerMinutes: () -> Float,
    baseMs: Long,
    glucose: Map<Long, DayGlucose>,
    lower: Int,
    upper: Int,
    label: AnnotatedString?,
    onDragStart: () -> Unit,
    onDrag: (deltaMinutes: Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: (offsetMinutes: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val band = targetRangeBandColor()
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val dragStart by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val tap by rememberUpdatedState(onTap)

    Box(modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragStart() },
                        onDragEnd = { dragEnd() },
                        onDragCancel = { dragEnd() },
                        // Dragging right pulls earlier time into view: the marker moves back.
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            drag(-amount / (size.width / JOURNAL_GRAPH_WINDOW_MIN))
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos -> tap((pos.x - size.width / 2f) / (size.width / JOURNAL_GRAPH_WINDOW_MIN)) }
                }
        ) {
            val half = JOURNAL_GRAPH_WINDOW_MIN / 2f
            val center = centerMinutes()
            val centerMs = baseMs + (center * MIN_MS).toLong()
            val startMs = centerMs - (half * MIN_MS).toLong()
            val endMs = centerMs + (half * MIN_MS).toLong()
            val pxPerMs = size.width / (JOURNAL_GRAPH_WINDOW_MIN * MIN_MS)
            fun xOf(ms: Long) = (ms - startMs) * pxPerMs

            val padTop = 8.dp.toPx()
            val padBottom = 16.dp.toPx()
            val plotH = size.height - padTop - padBottom

            // Visible readings, found by binary search in each cached day.
            val firstDay = dayOf(startMs)
            val lastDay = dayOf(endMs)
            var peak = 0
            for (day in firstDay..lastDay) {
                val d = glucose[day] ?: continue
                var i = firstAtOrAfter(d.times, startMs)
                while (i < d.times.size && d.times[i] <= endMs) { if (d.values[i] > peak) peak = d.values[i]; i++ }
            }
            val minY = 40f
            val maxY = maxOf(250f, kotlin.math.ceil(peak / 50f) * 50f)
            fun yOf(v: Int) = padTop + plotH * (1f - (v - minY) / (maxY - minY))

            drawRect(band, Offset(0f, yOf(upper)), androidx.compose.ui.geometry.Size(size.width, yOf(lower) - yOf(upper)))

            // Hour gridlines with small clock labels.
            val hourMs = 60 * MIN_MS
            val offset = java.util.TimeZone.getDefault().getOffset(startMs)
            var t = Math.floorDiv(startMs + offset, hourMs) * hourMs - offset + hourMs
            while (t <= endMs) {
                val x = xOf(t)
                drawLine(grid, Offset(x, padTop), Offset(x, padTop + plotH), strokeWidth = 1.dp.toPx())
                val hour = (Math.floorMod(t + offset, DAY_MS) / hourMs).toInt()
                if (hour % 2 == 0) {
                    val text = measurer.measure("%02d:00".format(hour), labelStyle)
                    drawText(text, topLeft = Offset(x - text.size.width / 2f, size.height - padBottom + 2.dp.toPx()))
                }
                t += hourMs
            }

            val radius = 2.5.dp.toPx()
            for (day in firstDay..lastDay) {
                val d = glucose[day] ?: continue
                var i = firstAtOrAfter(d.times, startMs)
                while (i < d.times.size && d.times[i] <= endMs) {
                    val v = d.values[i]
                    val color = when { v < lower -> BelowRange; v > upper -> AboveRange; else -> InRange }
                    drawCircle(color, radius, Offset(xOf(d.times[i]), yOf(v)))
                    i++
                }
            }

            // The marker: the highlighted entry's timestamp.
            drawMarker(markerColor, padTop, padTop + plotH)
        }
        if (label != null) {
            Row(Modifier.fillMaxSize().padding(top = 6.dp)) {
                Box(Modifier.weight(1f))
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopStart) {
                    Text(
                        label,
                        Modifier
                            .padding(start = 8.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = labelColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawMarker(color: Color, top: Float, bottom: Float) {
    val x = size.width / 2f
    drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = 1.5.dp.toPx())
    drawCircle(color, 3.5.dp.toPx(), Offset(x, top), style = Stroke(1.5.dp.toPx()))
}

private fun dayOf(ms: Long): Long = Math.floorDiv(ms + java.util.TimeZone.getDefault().getOffset(ms), DAY_MS)

/** Index of the first element >= [value] in the ascending [times]. */
private fun firstAtOrAfter(times: LongArray, value: Long): Int {
    var lo = 0
    var hi = times.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (times[mid] < value) lo = mid + 1 else hi = mid
    }
    return lo
}
