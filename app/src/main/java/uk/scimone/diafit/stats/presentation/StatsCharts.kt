package uk.scimone.diafit.stats.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.scimone.diafit.stats.domain.SleepNight
import uk.scimone.diafit.stats.domain.StatsTrendPoint
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** One bar: its axis label and stacked segments, or null segments when that day / month has no data. */
data class BarData(val label: String, val segments: List<Double>?)

/** A "nice" upper bound (1, 2, 2.5, 5 x 10^n) at or above [value], so gridlines fall on round numbers. */
internal fun niceCeiling(value: Double): Double {
    if (value <= 0) return 1.0
    val exp = floor(kotlin.math.log10(value))
    val base = 10.0.pow(exp)
    val f = value / base
    val nice = when { f <= 1 -> 1.0; f <= 2 -> 2.0; f <= 2.5 -> 2.5; f <= 5 -> 5.0; else -> 10.0 }
    return nice * base
}

/**
 * Vertical stacked bars, one per day (or month). The compact [showAxis] = false form is the card sparkline; with
 * an axis it has gridlines, value labels and tapping a bar selects it ([selected] / [onSelect]).
 */
@Composable
fun StatsBarChart(
    bars: List<BarData>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 96.dp,
    showAxis: Boolean = false,
    selected: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    /** Fixed top of the scale (e.g. 100 for percentages); otherwise the tallest bar, rounded up. */
    fixedMax: Double? = null,
    formatAxis: (Double) -> String = { it.roundToInt().toString() },
    /** Writes each bar's value above it (for the few bars of a week). */
    formatValue: ((Double) -> String)? = null,
    /** A dashed line at the period's average, to read each bar against it. */
    average: Double? = null,
    avgColor: Color? = null
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val top = fixedMax ?: niceCeiling(bars.maxOfOrNull { it.segments?.sum() ?: 0.0 } ?: 1.0)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(bars.size, onSelect) {
                if (onSelect != null) detectTapGestures { p ->
                    val axisW = if (showAxis) AXIS_WIDTH_DP.dp.toPx() else 0f
                    val slot = (size.width - axisW) / bars.size
                    val i = ((p.x - axisW) / slot).toInt()
                    onSelect(if (i in bars.indices && i != selected) i else null)
                }
            }
    ) {
        val axisW = if (showAxis) AXIS_WIDTH_DP.dp.toPx() else 0f
        val labelH = if (showAxis) 16.dp.toPx() else 0f
        val topPad = if (formatValue != null) 16.dp.toPx() else 0f
        val plotH = size.height - labelH - topPad
        val plotW = size.width - axisW
        translate(top = topPad) {
        if (showAxis) {
            val lines = 5
            for (i in 0..lines) {
                val v = top * i / lines
                val y = plotH - (plotH * (v / top)).toFloat()
                drawLine(grid, Offset(axisW, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                val text = measurer.measure(formatAxis(v), labelStyle)
                drawText(text, topLeft = Offset(0f, (y - text.size.height / 2f).coerceIn(0f, plotH - text.size.height)))
            }
        }
        val slot = plotW / bars.size
        val barW = (slot * if (bars.size > 14) 0.7f else 0.55f).coerceAtMost(28.dp.toPx())
        val labelEvery = when { bars.size <= 12 -> 1; bars.size <= 16 -> 2; else -> 5 }
        bars.forEachIndexed { i, bar ->
            val x = axisW + slot * i + (slot - barW) / 2
            val dim = selected != null && selected != i
            val segments = bar.segments
            val total = segments?.sum() ?: 0.0
            if (segments == null || total <= 0.0) {
                // A hairline marks "no data" so the gap doesn't read as a zero.
                drawLine(grid, Offset(x, plotH - 1.dp.toPx()), Offset(x + barW, plotH - 1.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            } else {
                val barH = (plotH * (total / top)).toFloat().coerceIn(3.dp.toPx(), plotH)
                val r = minOf(barW / 3, barH / 2)
                val shape = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(x, plotH - barH, x + barW, plotH, CornerRadius(r), CornerRadius(r), CornerRadius.Zero, CornerRadius.Zero))
                }
                // Segments are plain rectangles inside one rounded outline, so the stack reads as a single bar.
                clipPath(shape) {
                    var bottom = plotH
                    segments.forEachIndexed { s, v ->
                        if (v <= 0) return@forEachIndexed
                        val h = barH * (v / total).toFloat()
                        drawRect(colors[s].copy(alpha = if (dim) 0.35f else 1f), Offset(x, bottom - h), Size(barW, h))
                        bottom -= h
                    }
                }
                if (formatValue != null) {
                    val t = measurer.measure(formatValue(total), labelStyle)
                    drawText(t, topLeft = Offset(x + barW / 2 - t.size.width / 2f, plotH - barH - t.size.height - 1.dp.toPx()))
                }
            }
            if (showAxis && (i % labelEvery == 0)) {
                val text = measurer.measure(bar.label, labelStyle)
                drawText(text, topLeft = Offset(x + barW / 2 - text.size.width / 2f, plotH + 2.dp.toPx()))
            }
        }
        if (average != null && average > 0 && average <= top) {
            val y = plotH - (plotH * (average / top)).toFloat()
            drawLine(
                (avgColor ?: Color.White).copy(alpha = 0.55f), Offset(axisW, y), Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )
        }
        }
    }
}

private const val AXIS_WIDTH_DP = 36

/**
 * Sleep regularity: one floating bar per day from bedtime (top) to wake-up (bottom) on a clock axis, so a
 * regular sleeper gets a neat column and a drifting schedule shows as a staircase.
 */
@Composable
fun SleepScheduleChart(
    bars: List<BarData>,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 220.dp,
    selected: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    /** The overview form: no axis, labels or gridlines. */
    compact: Boolean = false
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val present = bars.mapNotNull { it.segments }
    val lo = floor((present.minOfOrNull { it[0] } ?: 240.0) / 120.0).toInt() * 120
    val hi = max(lo + 240, ceil((present.maxOfOrNull { it[1] } ?: 780.0) / 120.0).toInt() * 120)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(bars.size, onSelect) {
                if (onSelect != null) detectTapGestures { p ->
                    val slot = (size.width - AXIS_WIDTH_DP.dp.toPx()) / bars.size
                    val i = ((p.x - AXIS_WIDTH_DP.dp.toPx()) / slot).toInt()
                    onSelect(if (i in bars.indices && i != selected) i else null)
                }
            }
    ) {
        val axisW = if (compact) 0f else AXIS_WIDTH_DP.dp.toPx() + 8.dp.toPx()
        val labelH = if (compact) 0f else 16.dp.toPx()
        val plotH = size.height - labelH
        val plotW = size.width - axisW
        fun y(minutes: Double) = (plotH * (minutes - lo) / (hi - lo)).toFloat()
        var t = lo
        while (!compact && t <= hi) {
            drawLine(grid, Offset(axisW, y(t.toDouble())), Offset(size.width, y(t.toDouble())), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(formatClock(SleepNight.axisToClock(t)), labelStyle)
            drawText(text, topLeft = Offset(0f, (y(t.toDouble()) - text.size.height / 2f).coerceIn(0f, plotH - text.size.height)))
            t += 120
        }
        val slot = plotW / bars.size
        val barW = (slot * if (bars.size > 14) 0.7f else 0.55f).coerceAtMost(28.dp.toPx())
        val labelEvery = when { bars.size <= 12 -> 1; bars.size <= 16 -> 2; else -> 5 }
        bars.forEachIndexed { i, bar ->
            val x = axisW + slot * i + (slot - barW) / 2
            bar.segments?.let { (bed, wake) ->
                val dim = selected != null && selected != i
                drawRoundRect(
                    color.copy(alpha = if (dim) 0.35f else 1f),
                    topLeft = Offset(x, y(bed)),
                    size = Size(barW, (y(wake) - y(bed)).coerceAtLeast(3f)),
                    cornerRadius = CornerRadius(barW / 3, barW / 3)
                )
            }
            if (!compact && i % labelEvery == 0) {
                val text = measurer.measure(bar.label, labelStyle)
                drawText(text, topLeft = Offset(x + barW / 2 - text.size.width / 2f, plotH + 2.dp.toPx()))
            }
        }
    }
}

/** Averages of the last few periods as a line, the current one highlighted (the Trends chart). */
@Composable
fun StatsTrendChart(
    points: List<StatsTrendPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 130.dp
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val surface = MaterialTheme.colorScheme.surface
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val values = points.mapNotNull { it.value }
        if (points.isEmpty()) return@Canvas
        val labelH = 16.dp.toPx()
        val pad = 8.dp.toPx()
        val plotH = size.height - labelH - 2 * pad
        val lo = values.minOrNull() ?: 0.0
        val hi = values.maxOrNull() ?: 1.0
        // Keep a flat series away from the edges; otherwise pad the range a little.
        val span = if (hi - lo < 1e-9) max(1.0, hi * 0.2) else hi - lo
        val min = lo - span * 0.15
        val max = hi + span * 0.15
        fun x(i: Int) = size.width * (i + 0.5f) / points.size
        fun y(v: Double) = pad + plotH - (plotH * (v - min) / (max - min)).toFloat()
        drawLine(grid, Offset(0f, pad + plotH), Offset(size.width, pad + plotH), strokeWidth = 1.dp.toPx())
        val path = Path()
        var open = false
        points.forEachIndexed { i, p ->
            val v = p.value
            if (v == null) { open = false } else if (!open) { path.moveTo(x(i), y(v)); open = true } else path.lineTo(x(i), y(v))
        }
        drawPath(path, color.copy(alpha = 0.6f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        points.forEachIndexed { i, p ->
            p.value?.let { v ->
                val r = if (p.isCurrent) 6.dp.toPx() else 4.dp.toPx()
                drawCircle(if (p.isCurrent) color else surface, r, Offset(x(i), y(v)))
                drawCircle(color, r, Offset(x(i), y(v)), style = Stroke(2.dp.toPx()))
            }
            val text = measurer.measure(p.label, labelStyle)
            drawText(text, topLeft = Offset(x(i) - text.size.width / 2f, size.height - labelH + 2.dp.toPx()))
        }
    }
}

/** A thin horizontal bar split into proportional segments (glucose zones, sleep stages). */
@Composable
fun SegmentedBar(
    shares: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 16.dp
) {
    val visible = shares.filter { it.first > 0f }
    Row(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (visible.isEmpty()) Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))
        visible.forEach { (share, c) -> Box(Modifier.weight(share).fillMaxHeight().background(c)) }
    }
}
