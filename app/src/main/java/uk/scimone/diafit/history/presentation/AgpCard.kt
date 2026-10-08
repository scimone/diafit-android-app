package uk.scimone.diafit.history.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.AgpProfile
import uk.scimone.diafit.core.domain.model.gaussianSmoothCircular
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.history.presentation.components.ActivityStrip
import uk.scimone.diafit.history.presentation.components.CARBS_FULL_INTENSITY_G
import uk.scimone.diafit.history.presentation.components.INSULIN_FULL_INTENSITY_U
import uk.scimone.diafit.history.presentation.components.TreatmentStrip
import uk.scimone.diafit.history.presentation.components.drawHourGuides
import uk.scimone.diafit.history.presentation.components.hourGuideColor
import uk.scimone.diafit.history.presentation.model.AgpMarkers
import uk.scimone.diafit.history.presentation.model.REFERENCE_DAY_MS
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.Activity
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.InRange

private const val Y_MIN = 40f
private const val EDGE_PAD_DP = 6

/**
 * Ambulatory glucose profile: median line with 25–75 % (strong) and 5–95 % (faint) bands over the 24 h clock,
 * coloured by where they sit against the target range (below / in / above). It spans the full card width with
 * no axis of its own, so its hours line up with the time axis and day tracks below.
 */
@Composable
internal fun AgpCard(agp: AgpProfile?, markers: AgpMarkers, thresholds: GlucoseThresholds, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        if (agp == null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No glucose readings in this period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else Column(Modifier.fillMaxSize()) {
            AgpPlot(agp.toBands(), thresholds, Modifier.weight(1f).fillMaxWidth())
            // The day tracks' own strips, fed every day folded onto one reference day and drawn fainter so overlaps deepen.
            Spacer(Modifier.height(3.dp))
            TreatmentStrip(markers.carbs, 0L, REFERENCE_DAY_MS, Carbs, CARBS_FULL_INTENSITY_G, alphaScale = markers.alphaScale)
            Spacer(Modifier.height(1.dp))
            TreatmentStrip(markers.bolus, 0L, REFERENCE_DAY_MS, Bolus, INSULIN_FULL_INTENSITY_U, alphaScale = markers.alphaScale)
            Spacer(Modifier.height(1.dp))
            ActivityStrip(ActivityData(), markers.activity, 0L, REFERENCE_DAY_MS, alphaScale = markers.alphaScale, sleepShare = markers.sleepShare)
        }
    }
}

/** The five AGP curves, any bin count (History: 96 bins, 5–95 %; Patterns: 288 bins, 10–90 %). NaN = no data. */
internal class AgpBands(
    val outerLow: FloatArray,
    val low: FloatArray,
    val median: FloatArray,
    val high: FloatArray,
    val outerHigh: FloatArray
) {
    val bins: Int get() = median.size

    /** Every curve blurred with [gaussianSmoothCircular], so the chart reads as curves rather than bin-to-bin noise. */
    fun smoothed(): AgpBands {
        fun FloatArray.blur(): FloatArray {
            val d = DoubleArray(size) { this[it].toDouble() }.gaussianSmoothCircular()
            return FloatArray(size) { d[it].toFloat() }
        }
        return AgpBands(outerLow.blur(), low.blur(), median.blur(), high.blur(), outerHigh.blur())
    }
}


internal fun AgpProfile.toBands() = AgpBands(p5, p25, median, p75, p95)

/**
 * The AGP chart without card or axis. [highlights] are stretches of the day (start/end hour, wrapping midnight
 * when start > end) washed in [highlightColor] while the rest of the day is dimmed. [yLabels] writes the target
 * range limits at the left edge.
 */
@Composable
internal fun AgpPlot(
    bands: AgpBands,
    thresholds: GlucoseThresholds,
    modifier: Modifier,
    highlights: List<Pair<Int, Int>> = emptyList(),
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    yLabels: Boolean = false,
    /** False when [bands] are already smoothed (the pattern AGP is, so detection sees the drawn curves). */
    smooth: Boolean = true
) {
    val guides = hourGuideColor()
    val targetFill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val dim = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val bands = remember(bands, smooth) { if (smooth) bands.smoothed() else bands }
    val yMax = remember(bands) {
        val top = bands.outerHigh.filter { !it.isNaN() }.maxOrNull() ?: 250f
        maxOf(250f, kotlin.math.ceil(top / 50f) * 50f).coerceAtMost(400f)
    }
    Canvas(modifier) {
        val n = bands.bins
        val top = EDGE_PAD_DP.dp.toPx()
        val h = size.height - 2 * top
        val w = size.width
        // Bin centres, extended to both edges so the curves span the whole day like the tracks.
        fun x(bin: Int) = when {
            bin < 0 -> 0f
            bin >= n -> w
            else -> w * (bin + 0.5f) / n
        }
        fun y(v: Float) = top + h * (1f - (v.coerceIn(Y_MIN, yMax) - Y_MIN) / (yMax - Y_MIN))
        val yHigh = y(thresholds.high.toFloat())
        val yLow = y(thresholds.low.toFloat())
        // Highlighted stretches as x ranges, midnight-wrapping ones split in two.
        val lit = highlights.flatMap { (start, end) ->
            if (start > end) listOf(start * w / 24f to w, 0f to end * w / 24f) else listOf(start * w / 24f to end * w / 24f)
        }.sortedBy { it.first }.fold(mutableListOf<Pair<Float, Float>>()) { merged, r ->
            // Overlapping stretches (night contains dawn) become one, so washes don't stack and no edge sits inside.
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.second) merged[merged.size - 1] = last.first to maxOf(last.second, r.second) else merged += r
            merged
        }

        drawRect(targetFill, Offset(0f, yHigh), Size(w, yLow - yHigh))
        for ((from, to) in lit) drawRect(highlightColor.copy(alpha = 0.14f), Offset(from, 0f), Size(to - from, size.height))
        drawHourGuides(guides)

        // Each band is drawn three times, clipped to the zone it falls in, so it takes that zone's colour.
        val zones = listOf(
            Triple(0f, yHigh, AboveRange),
            Triple(yHigh, yLow, InRange),
            Triple(yLow, size.height, BelowRange)
        )
        for ((zTop, zBottom, color) in zones) clipRect(0f, zTop, w, zBottom) {
            band(bands.outerLow, bands.outerHigh, ::x, ::y, color.copy(alpha = 0.28f))
            band(bands.low, bands.high, ::x, ::y, color.copy(alpha = 0.75f))
        }

        val line = Path()
        forEachRun(bands.median, bands.median) { i, j ->
            line.curveThrough(edgePoints(bands.median, i, j, n, w, ::x, ::y), moveFirst = true)
        }
        // Same zone clipping as the bands: the line takes the zone's colour, lightened so it stands out from the bands.
        val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        for ((zTop, zBottom, color) in zones) clipRect(0f, zTop, w, zBottom) {
            drawPath(line, androidx.compose.ui.graphics.lerp(color, Color.White, 0.4f), style = stroke)
        }

        if (lit.isNotEmpty()) {
            // Dim everything outside the highlighted stretches, and edge them so they read as a selection.
            val edges = lit.flatMap { listOf(it.first, it.second) }.filter { it > 0f && it < w }.sorted()
            var cursor = 0f
            for ((from, to) in lit) {
                if (from > cursor) drawRect(dim, Offset(cursor, 0f), Size(from - cursor, size.height))
                cursor = maxOf(cursor, to)
            }
            if (cursor < w) drawRect(dim, Offset(cursor, 0f), Size(w - cursor, size.height))
            for (e in edges) drawLine(highlightColor, Offset(e, 0f), Offset(e, size.height), 1.5.dp.toPx())
        }

        if (yLabels) for (v in listOf(thresholds.low, thresholds.high)) {
            val text = measurer.measure(v.toString(), labelStyle)
            drawText(text, topLeft = Offset(4.dp.toPx(), y(v.toFloat()) - text.size.height - 1.dp.toPx()))
        }
    }
}

/** Fills the area between [lo] and [hi] as one curved path per run of bins that have data, edges extended to the card sides. */
private fun DrawScope.band(lo: FloatArray, hi: FloatArray, x: (Int) -> Float, y: (Float) -> Float, color: Color) {
    val n = lo.size
    forEachRun(lo, hi) { i, j ->
        val p = Path()
        p.curveThrough(edgePoints(hi, i, j, n, size.width, x, y), moveFirst = true)
        p.curveThrough(edgePoints(lo, i, j, n, size.width, x, y).asReversed(), moveFirst = false)
        p.close()
        drawPath(p, color)
    }
}

/** Calls [block] with the first and last bin of every run where both arrays have data. */
private inline fun forEachRun(a: FloatArray, b: FloatArray, block: (Int, Int) -> Unit) {
    val n = a.size
    var i = 0
    while (i < n) {
        if (a[i].isNaN() || b[i].isNaN()) { i++; continue }
        var j = i
        while (j + 1 < n && !a[j + 1].isNaN() && !b[j + 1].isNaN()) j++
        block(i, j)
        i = j + 1
    }
}

/** The points of [v] over bins [i]..[j], stretched to the chart's left/right edge when the run touches it. */
private fun edgePoints(v: FloatArray, i: Int, j: Int, n: Int, width: Float, x: (Int) -> Float, y: (Float) -> Float): List<Offset> =
    buildList {
        if (i == 0) add(Offset(0f, y(v[0])))
        for (k in i..j) add(Offset(x(k), y(v[k])))
        if (j == n - 1) add(Offset(width, y(v[j])))
    }

/** A smooth curve through [points]: quadratic segments between their midpoints, with each point as control. */
private fun Path.curveThrough(points: List<Offset>, moveFirst: Boolean) {
    if (points.isEmpty()) return
    val first = points[0]
    if (moveFirst) moveTo(first.x, first.y) else lineTo(first.x, first.y)
    for (k in 1 until points.size) {
        val prev = points[k - 1]
        val cur = points[k]
        quadraticTo(prev.x, prev.y, (prev.x + cur.x) / 2, (prev.y + cur.y) / 2)
    }
    val last = points.last()
    lineTo(last.x, last.y)
}
