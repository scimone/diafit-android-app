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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import uk.scimone.diafit.journal.presentation.DayGlucose
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.InRange
import uk.scimone.diafit.ui.theme.targetRangeBandColor

/** The graph shows this many minutes, with the marker (the highlighted entry's timestamp) in the middle. */
const val JOURNAL_GRAPH_WINDOW_MIN = 480f

/** Where the selected entry sits across the graph: 2 h in for a point (so its aftermath shows), the middle for a stretch. */
const val JOURNAL_ANCHOR_POINT = 0.25f
const val JOURNAL_ANCHOR_STRETCH = 0.5f

/** Carb absorption (g/h) and insulin activity (U/min) the curves are scaled against at least, so small amounts stay small. */
private const val MIN_CARB_SCALE_GPH = 20.0
private const val MIN_INSULIN_SCALE = 0.02
private const val INSULIN_REACH_H = 4.5
private const val MIN_MS = 60_000L
private const val DAY_MS = 24 * 60 * MIN_MS

/**
 * A journal entry on the graph: a point in time (meal, insulin, device event) when [endMs] is null, else the
 * whole stretch it covers (low/high, sleep, workout).
 */
class GraphMarker(
    val startMs: Long,
    val endMs: Long?,
    val color: Color,
    val afterMs: Long? = null,
    /** A second colour for an entry that stands for two things (a meal with insulin): the dot is split in two. */
    val extraColor: Color? = null,
    /** The glucose peak after a meal, marked on the graph with its value and time. */
    val peakMs: Long? = null,
    val peakMgdl: Int? = null,
    /** A meal's carb absorption curve, drawn faintly at the bottom: grams, per-course times and duration. */
    val carbs: CarbCurve? = null,
    /** An insulin entry: the graph shows the summed insulin activity of all doses. */
    val insulin: Boolean = false
) {
    val colors: List<Color> get() = listOfNotNull(color, extraColor)
}

/** One absorption curve per course; the graph draws their sum. */
class CarbCurve(val courses: List<uk.scimone.diafit.journal.presentation.model.CarbCourse>)

/**
 * Glucose around the highlighted journal entry, in the style of Home's glucose panel (target band, dots
 * coloured by range) but fixed at one zoom level. It is drawn directly from the few cached days in
 * [glucose], so scrolling never has to build a chart model. [centerMinutes] is the marker position in minutes
 * after [baseMs]; it is read while drawing only, so moving it redraws without recomposing.
 */
@Composable
fun JournalGlucoseGraph(
    centerMinutes: () -> Float,
    anchorFraction: () -> Float,
    baseMs: Long,
    glucose: Map<Long, DayGlucose>,
    lower: Int,
    upper: Int,
    markers: List<GraphMarker>,
    selectedMarker: Int,
    allCarbCourses: List<uk.scimone.diafit.journal.presentation.model.CarbCourse>,
    allDoses: List<uk.scimone.diafit.journal.presentation.model.BolusPartUi>,
    onDragStart: () -> Unit,
    onDrag: (deltaMinutes: Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: (offsetMinutes: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val band = targetRangeBandColor()
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val ringColor = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val dragStart by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val tap by rememberUpdatedState(onTap)
    val anchor by rememberUpdatedState(anchorFraction)
    // Each course's end is looked up once, not on every frame of a drag.
    val carbSpans = remember(allCarbCourses) {
        allCarbCourses.filter { it.grams > 0.0 }.map { it to it.curve.carbsEndTimeUtc() }
    }
    val doseList = remember(allDoses) { allDoses.sortedBy { it.timeUtc } }

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
                    detectTapGestures { pos -> tap((pos.x - size.width * anchor()) / (size.width / JOURNAL_GRAPH_WINDOW_MIN)) }
                }
        ) {
            val center = centerMinutes()
            val centerMs = baseMs + (center * MIN_MS).toLong()
            val startMs = centerMs - (JOURNAL_GRAPH_WINDOW_MIN * anchor() * MIN_MS).toLong()
            val endMs = startMs + (JOURNAL_GRAPH_WINDOW_MIN * MIN_MS).toLong()
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

            // The selected meal's glucose peak: its time goes at the bottom, replacing any axis label it would overlap.
            val peakMarker = markers.getOrNull(selectedMarker)?.takeIf { it.peakMs != null && it.peakMgdl != null && it.peakMs in startMs..endMs }
            val peakTime = peakMarker?.let {
                val t = measurer.measure(java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it.peakMs!!)), labelStyle.copy(fontWeight = FontWeight.Bold, color = it.color))
                val left = (xOf(it.peakMs!!) - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
                t to left
            }

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
                    val left = x - text.size.width / 2f
                    val clashes = peakTime?.let { (pt, pl) -> left < pl + pt.size.width + 4.dp.toPx() && left + text.size.width + 4.dp.toPx() > pl } == true
                    if (!clashes) drawText(text, topLeft = Offset(left, size.height - padBottom + 2.dp.toPx()))
                }
                t += hourMs
            }

            // The highlighted entry only: a stretch of time marks its whole area.
            val plotTop = padTop
            val plotBottom = padTop + plotH
            // A meal: the hours its carbs keep acting after it was eaten.
            markers.getOrNull(selectedMarker)?.let { m ->
                val after = m.afterMs ?: return@let
                if (after < startMs || m.startMs > endMs) return@let
                val x0 = xOf(m.startMs).coerceAtLeast(0f)
                val x1 = xOf(after).coerceAtMost(size.width)
                drawRect(m.color.copy(alpha = 0.06f), Offset(x0, 0f), androidx.compose.ui.geometry.Size((x1 - x0).coerceAtLeast(2.dp.toPx()), plotBottom))
                if (xOf(after) <= size.width) drawLine(m.color.copy(alpha = 0.4f), Offset(x1, 0f), Offset(x1, plotBottom), strokeWidth = 1.dp.toPx())
            }
            run {
                val highlighted = true
                markers.getOrNull(selectedMarker)?.let { m ->
                    val end = m.endMs ?: return@let
                    if (end < startMs || m.startMs > endMs) return@let
                    val x0 = xOf(m.startMs).coerceAtLeast(0f)
                    val x1 = xOf(end).coerceAtMost(size.width)
                    // Right up to the top of the chart, with no edge lines.
                    drawRect(m.color.copy(alpha = 0.06f), Offset(x0, 0f), androidx.compose.ui.geometry.Size((x1 - x0).coerceAtLeast(2.dp.toPx()), plotBottom))
                    // Subtle edges, only where the stretch really starts / ends inside the view.
                    val edge = 1.dp.toPx()
                    if (xOf(m.startMs) >= 0f) drawLine(m.color.copy(alpha = 0.4f), Offset(x0, 0f), Offset(x0, plotBottom), strokeWidth = edge)
                    if (xOf(end) <= size.width) drawLine(m.color.copy(alpha = 0.4f), Offset(x1, 0f), Offset(x1, plotBottom), strokeWidth = edge)
                }
            }

            // The total carb absorption of every meal in view (not only the highlighted one), behind the readings.
            val selected = markers.getOrNull(selectedMarker)
            if (selected?.carbs != null) {
                val stepMs = 3 * MIN_MS
                val acting = carbSpans.filter { (c, end) -> c.timeUtc <= endMs && end >= startMs }
                if (acting.isNotEmpty()) {
                    fun total(t: Long): Double { var sum = 0.0; for ((c, end) in acting) if (t in c.timeUtc..end) sum += c.curve.carbsRateGphAt(t); return sum }
                    var peakRate = MIN_CARB_SCALE_GPH
                    var tp = startMs
                    while (tp <= endMs) { peakRate = maxOf(peakRate, total(tp)); tp += stepMs }
                    val path = androidx.compose.ui.graphics.Path()
                    path.moveTo(0f, plotBottom)
                    var t1 = startMs
                    while (t1 < endMs) { path.lineTo(xOf(t1), plotBottom - (total(t1) / peakRate).toFloat() * plotH * 0.4f); t1 += stepMs }
                    path.lineTo(size.width, plotBottom)
                    path.close()
                    drawPath(path, selected.color.copy(alpha = 0.16f))
                }
            }

            // The total insulin activity of every dose in view when an insulin entry is highlighted.
            if (selected?.insulin == true) {
                val stepMs = 3 * MIN_MS
                val reach = (INSULIN_REACH_H * 60 * MIN_MS).toLong()
                val acting = doseList.filter { it.timeUtc <= endMs && it.timeUtc + reach >= startMs }
                if (acting.isNotEmpty()) {
                    fun total(t: Long): Double { var sum = 0.0; for (d in acting) if (t >= d.timeUtc) sum += uk.scimone.diafit.core.domain.model.InsulinActivity.calculate(d.units, d.timeUtc, t).activity; return sum }
                    var peakAct = MIN_INSULIN_SCALE
                    var tp = startMs
                    while (tp <= endMs) { peakAct = maxOf(peakAct, total(tp)); tp += stepMs }
                    val path = androidx.compose.ui.graphics.Path()
                    path.moveTo(0f, plotBottom)
                    var t1 = startMs
                    while (t1 < endMs) { path.lineTo(xOf(t1), plotBottom - (total(t1) / peakAct).toFloat() * plotH * 0.4f); t1 += stepMs }
                    path.lineTo(size.width, plotBottom)
                    path.close()
                    drawPath(path, selected.color.copy(alpha = 0.22f))
                }
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

            // Point entries: a dot on top, the highlighted one bigger with a line down through the graph.
            // Dots that sit on top of each other share one circle, split vertically into their colours.
            val points = markers.withIndex().filter { (_, m) ->
                m.endMs == null && m.startMs >= startMs && m.startMs <= endMs
            }
            val used = HashSet<Int>()
            fun drawGroup(group: List<IndexedValue<GraphMarker>>) {
                val x = xOf(group.first().value.startMs)
                val picked = group.any { it.index == selectedMarker }
                val r = if (picked) 6.dp.toPx() else 4.dp.toPx()
                if (picked) drawLine(group.first { it.index == selectedMarker }.value.color.copy(alpha = 0.9f), Offset(x, plotTop), Offset(x, plotBottom), strokeWidth = 2.dp.toPx())
                drawCircle(ringColor, r + 1.5.dp.toPx(), Offset(x, plotTop))
                val colors = group.flatMap { it.value.colors }.distinct()
                val sliceW = 2 * r / colors.size
                colors.forEachIndexed { k, c ->
                    clipRect(left = x - r + sliceW * k, top = plotTop - r, right = x - r + sliceW * (k + 1), bottom = plotTop + r) {
                        drawCircle(c, r, Offset(x, plotTop))
                    }
                }
            }
            val groups = ArrayList<List<IndexedValue<GraphMarker>>>()
            for (pt in points) {
                if (!used.add(pt.index)) continue
                val x = xOf(pt.value.startMs)
                val group = listOf(pt) + points.filter { o -> o.index !in used && kotlin.math.abs(xOf(o.value.startMs) - x) < 6.dp.toPx() }.onEach { used.add(it.index) }
                groups.add(group)
            }
            groups.filter { g -> g.none { it.index == selectedMarker } }.forEach(::drawGroup)
            groups.filter { g -> g.any { it.index == selectedMarker } }.forEach(::drawGroup)

            if (peakMarker != null && peakTime != null) {
                val px = xOf(peakMarker.peakMs!!)
                val py = yOf(peakMarker.peakMgdl!!)
                val pc = when { peakMarker.peakMgdl < lower -> BelowRange; peakMarker.peakMgdl > upper -> AboveRange; else -> InRange }
                drawCircle(ringColor, 6.dp.toPx(), Offset(px, py))
                drawCircle(pc, 4.5.dp.toPx(), Offset(px, py))
                val num = measurer.measure("${peakMarker.peakMgdl}", labelStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = pc))
                val nx = if (px + 9.dp.toPx() + num.size.width > size.width) px - 9.dp.toPx() - num.size.width else px + 9.dp.toPx()
                drawText(num, topLeft = Offset(nx, (py - num.size.height / 2f).coerceIn(0f, plotBottom - num.size.height)))
                drawText(peakTime.first, topLeft = Offset(peakTime.second, size.height - padBottom + 2.dp.toPx()))
            }
        }
    }
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
