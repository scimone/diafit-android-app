package uk.scimone.diafit.history.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.AgpProfile
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.ui.theme.AboveRange
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
internal fun AgpCard(agp: AgpProfile?, thresholds: GlucoseThresholds, modifier: Modifier = Modifier) {
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
        } else AgpPlot(agp, thresholds, Modifier.fillMaxWidth())
    }
}

@Composable
private fun AgpPlot(agp: AgpProfile, thresholds: GlucoseThresholds, modifier: Modifier) {
    val targetFill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val yMax = remember(agp) {
        val top = agp.p95.filter { !it.isNaN() }.maxOrNull() ?: 250f
        maxOf(250f, kotlin.math.ceil(top / 50f) * 50f).coerceAtMost(400f)
    }
    Canvas(modifier) {
        val top = EDGE_PAD_DP.dp.toPx()
        val h = size.height - 2 * top
        val w = size.width
        // Bin centres, extended to both edges so the curves span the whole day like the tracks.
        fun x(bin: Int) = when {
            bin < 0 -> 0f
            bin >= AgpProfile.BINS -> w
            else -> w * (bin + 0.5f) / AgpProfile.BINS
        }
        fun y(v: Float) = top + h * (1f - (v.coerceIn(Y_MIN, yMax) - Y_MIN) / (yMax - Y_MIN))
        val yHigh = y(thresholds.high.toFloat())
        val yLow = y(thresholds.low.toFloat())

        drawRect(targetFill, Offset(0f, yHigh), Size(w, yLow - yHigh))

        // Each band is drawn three times, clipped to the zone it falls in, so it takes that zone's colour.
        val zones = listOf(
            Triple(0f, yHigh, AboveRange),
            Triple(yHigh, yLow, InRange),
            Triple(yLow, size.height, BelowRange)
        )
        for ((zTop, zBottom, color) in zones) clipRect(0f, zTop, w, zBottom) {
            band(agp.p5, agp.p95, ::x, ::y, color.copy(alpha = 0.28f))
            band(agp.p25, agp.p75, ::x, ::y, color.copy(alpha = 0.75f))
        }

        val line = Path()
        var open = false
        for (i in 0 until AgpProfile.BINS) {
            val v = agp.median[i]
            if (v.isNaN()) { open = false; continue }
            if (open) line.lineTo(x(i), y(v)) else { line.moveTo(if (i == 0) 0f else x(i), y(v)); open = true }
        }
        drawPath(
            line, InRange,
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** Fills the area between [lo] and [hi] as one path per run of bins that have data, edges extended to the card sides. */
private fun DrawScope.band(lo: FloatArray, hi: FloatArray, x: (Int) -> Float, y: (Float) -> Float, color: Color) {
    val n = AgpProfile.BINS
    var i = 0
    while (i < n) {
        if (lo[i].isNaN() || hi[i].isNaN()) { i++; continue }
        var j = i
        while (j + 1 < n && !lo[j + 1].isNaN() && !hi[j + 1].isNaN()) j++
        val p = Path()
        if (i == 0) p.moveTo(0f, y(hi[0])).also { p.lineTo(x(0), y(hi[0])) } else p.moveTo(x(i), y(hi[i]))
        for (k in i + 1..j) p.lineTo(x(k), y(hi[k]))
        if (j == n - 1) p.lineTo(x(n), y(hi[j]).also { })
        if (j == n - 1) p.lineTo(x(n), y(lo[j]))
        for (k in j downTo i) p.lineTo(x(k), y(lo[k]))
        if (i == 0) p.lineTo(0f, y(lo[0]))
        p.close()
        drawPath(p, color)
        i = j + 1
    }
}
