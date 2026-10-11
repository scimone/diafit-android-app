package uk.scimone.diafit.insights.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A hand-drawn sketch of a pattern in unit coordinates (x right, y down): its line, threshold guides and a meal tick. */
private class Sketch(
    val main: List<Pair<Float, Float>>,
    val guides: List<Float> = emptyList(),
    val meal: Float? = null,
    val second: List<Pair<Float, Float>>? = null,
    val bars: List<Float>? = null
)

private val SKETCHES: Map<String, Sketch> = mapOf(
    "low" to Sketch(listOf(0f to .35f, .25f to .4f, .5f to .92f, .75f to .4f, 1f to .35f), guides = listOf(.65f)),
    "high" to Sketch(listOf(0f to .65f, .25f to .6f, .5f to .08f, .75f to .6f, 1f to .65f), guides = listOf(.35f)),
    "variability" to Sketch(listOf(0f to .5f, .17f to .1f, .34f to .9f, .5f to .15f, .67f to .85f, .84f to .25f, 1f to .5f)),
    "steady" to Sketch(listOf(0f to .5f, .3f to .47f, .6f to .53f, 1f to .5f), guides = listOf(.28f, .72f)),
    "dawn" to Sketch(listOf(0f to .88f, .3f to .82f, .55f to .55f, .8f to .25f, 1f to .15f), guides = listOf(.5f)),
    "weekend" to Sketch(listOf(0f to .35f, .25f to .25f, .5f to .4f, .75f to .3f, 1f to .38f), second = listOf(0f to .7f, .3f to .8f, .6f to .6f, 1f to .75f)),
    "meal-low" to Sketch(listOf(0f to .5f, .15f to .5f, .35f to .2f, .55f to .55f, .8f to .93f, 1f to .8f), guides = listOf(.75f), meal = .15f),
    "meal-spike" to Sketch(listOf(0f to .65f, .15f to .65f, .33f to .08f, .55f to .5f, .8f to .65f, 1f to .65f), guides = listOf(.35f), meal = .15f),
    "meal-slow" to Sketch(listOf(0f to .75f, .15f to .75f, .4f to .3f, .65f to .25f, .85f to .3f, 1f to .38f), guides = listOf(.45f), meal = .15f),
    "meal-good" to Sketch(listOf(0f to .65f, .15f to .65f, .4f to .42f, .65f to .55f, .85f to .65f, 1f to .65f), meal = .15f),
    "meal-rich" to Sketch(listOf(0f to .75f, .15f to .75f, .4f to .35f, .65f to .25f, .85f to .3f, 1f to .35f), meal = .15f, second = listOf(0f to .75f, .15f to .75f, .35f to .5f, .6f to .7f, 1f to .75f)),
    "meal-type" to Sketch(emptyList(), bars = listOf(.85f, .5f, .6f)),
    "meal-bolus" to Sketch(listOf(0f to .75f, .15f to .75f, .38f to .5f, .6f to .68f, 1f to .75f), meal = .15f, second = listOf(0f to .75f, .15f to .75f, .4f to .15f, .65f to .5f, 1f to .72f))
)

private fun DrawScope.curve(points: List<Pair<Float, Float>>, color: Color, width: Float, dashed: Boolean = false) {
    if (points.size < 2) return
    val pts = points.map { Offset(it.first * size.width, it.second * size.height) }
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val mid = Offset((pts[i - 1].x + pts[i].x) / 2, (pts[i - 1].y + pts[i].y) / 2)
            if (i == 1) lineTo(mid.x, mid.y) else quadraticTo(pts[i - 1].x, pts[i - 1].y, mid.x, mid.y)
        }
        lineTo(pts.last().x, pts.last().y)
    }
    drawPath(
        path, color,
        style = Stroke(width = width, cap = StrokeCap.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 6f)) else null)
    )
}

/**
 * The little schematic on a compact card: a sketch of what the pattern is about (a dip under the low line, a spike
 * after a meal, a flat line between the range guides...), keyed by the detector that found it.
 */
@Composable
fun PatternGlyph(detector: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val sketch = SKETCHES[detector] ?: SKETCHES.getValue("steady")
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f))) {
        Canvas(Modifier.fillMaxSize().padding(7.dp)) {
            val stroke = 2.2.dp.toPx()
            val faint = tint.copy(alpha = 0.4f)
            for (g in sketch.guides) {
                val y = g * this.size.height
                drawLine(faint, Offset(0f, y), Offset(this.size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            }
            sketch.meal?.let { drawLine(faint, Offset(it * this.size.width, 0f), Offset(it * this.size.width, this.size.height), strokeWidth = 1.dp.toPx()) }
            sketch.bars?.let { bars ->
                val w = this.size.width / (bars.size * 2 - 1)
                bars.forEachIndexed { i, h ->
                    val top = this.size.height * (1f - h)
                    drawRoundRect(tint.copy(alpha = if (i == 0) 1f else 0.45f), Offset(i * 2 * w, top), androidx.compose.ui.geometry.Size(w, this.size.height - top), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                }
            }
            sketch.second?.let { curve(it, faint, stroke, dashed = true) }
            curve(sketch.main, tint, stroke)
        }
    }
}
