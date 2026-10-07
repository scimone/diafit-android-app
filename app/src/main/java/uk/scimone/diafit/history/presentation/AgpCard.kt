package uk.scimone.diafit.history.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.scimone.diafit.core.domain.model.AgpProfile
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.ui.theme.InRange

private const val Y_MIN = 40f

/** Ambulatory glucose profile: median line with 25–75 % and 5–95 % bands over the 24 h clock, against the target range. */
@Composable
internal fun AgpCard(agp: AgpProfile?, thresholds: GlucoseThresholds, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)) {
            Text("Glucose profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Median, 25–75 % and 5–95 % of the period, by time of day",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            if (agp == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No glucose readings in this period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else AgpPlot(agp, thresholds, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun AgpPlot(agp: AgpProfile, thresholds: GlucoseThresholds, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val median = MaterialTheme.colorScheme.primary
    val yMax = remember(agp) {
        val top = agp.p95.filter { !it.isNaN() }.maxOrNull() ?: 250f
        maxOf(250f, kotlin.math.ceil(top / 50f) * 50f).coerceAtMost(400f)
    }
    // Paths are rebuilt only when the profile changes, not on every draw.
    Canvas(modifier) {
        val left = 24.dp.toPx()
        val bottom = size.height - 12.dp.toPx()
        val w = size.width - left
        fun x(bin: Int) = left + w * (bin + 0.5f) / AgpProfile.BINS
        fun y(v: Float) = bottom * (1f - (v.coerceIn(Y_MIN, yMax) - Y_MIN) / (yMax - Y_MIN))

        // Target range.
        drawRect(
            InRange.copy(alpha = 0.12f),
            Offset(left, y(thresholds.high.toFloat())),
            androidx.compose.ui.geometry.Size(w, y(thresholds.low.toFloat()) - y(thresholds.high.toFloat()))
        )
        val dash = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        listOf(thresholds.low, thresholds.high).forEach { t ->
            drawLine(InRange.copy(alpha = 0.7f), Offset(left, y(t.toFloat())), Offset(left + w, y(t.toFloat())), 1.dp.toPx(), pathEffect = dash.pathEffect)
            val r = measurer.measure(t.toString(), label)
            drawText(r, topLeft = Offset(left - r.size.width - 3.dp.toPx(), y(t.toFloat()) - r.size.height / 2f))
        }
        // 6-hourly time guides and labels.
        for (h in 0..24 step 6) {
            val gx = left + w * h / 24f
            drawLine(grid, Offset(gx, 0f), Offset(gx, bottom), 1f)
            val r = measurer.measure("%02d".format(h % 24).let { if (h == 24) "24" else it }, label)
            val tx = (gx - r.size.width / 2f).coerceIn(left - 4.dp.toPx(), size.width - r.size.width)
            drawText(r, topLeft = Offset(tx, bottom + 1.dp.toPx()))
        }

        band(agp.p5, agp.p95, ::x, ::y, median.copy(alpha = 0.14f))
        band(agp.p25, agp.p75, ::x, ::y, median.copy(alpha = 0.30f))
        val line = Path()
        var open = false
        for (i in 0 until AgpProfile.BINS) {
            val v = agp.median[i]
            if (v.isNaN()) { open = false; continue }
            if (open) line.lineTo(x(i), y(v)) else { line.moveTo(x(i), y(v)); open = true }
        }
        drawPath(line, median, style = Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** Fills the area between [lo] and [hi] as one path per run of bins that have data. */
private fun DrawScope.band(lo: FloatArray, hi: FloatArray, x: (Int) -> Float, y: (Float) -> Float, color: Color) {
    var i = 0
    while (i < AgpProfile.BINS) {
        if (lo[i].isNaN() || hi[i].isNaN()) { i++; continue }
        var j = i
        while (j + 1 < AgpProfile.BINS && !lo[j + 1].isNaN() && !hi[j + 1].isNaN()) j++
        val p = Path()
        p.moveTo(x(i), y(hi[i]))
        for (k in i + 1..j) p.lineTo(x(k), y(hi[k]))
        for (k in j downTo i) p.lineTo(x(k), y(lo[k]))
        p.close()
        drawPath(p, color)
        i = j + 1
    }
}
