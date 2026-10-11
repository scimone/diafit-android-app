package uk.scimone.diafit.patterns.presentation

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
    val bars: List<Float>? = null,
    /** A shaded stretch (the activity itself), as x from-to. */
    val span: Pair<Float, Float>? = null
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
    "ex-drop" to Sketch(listOf(0f to .4f, .15f to .4f, .35f to .6f, .5f to .85f, .75f to .8f, 1f to .75f), guides = listOf(.7f), span = .15f to .5f),
    "ex-low" to Sketch(listOf(0f to .4f, .15f to .4f, .5f to .5f, .7f to .9f, 1f to .78f), guides = listOf(.7f), span = .15f to .4f),
    "ex-delayed" to Sketch(listOf(0f to .4f, .3f to .4f, .55f to .5f, .8f to .92f, 1f to .9f), guides = listOf(.7f), span = .1f to .3f),
    "ex-rise" to Sketch(listOf(0f to .7f, .15f to .7f, .4f to .2f, .65f to .35f, 1f to .6f), guides = listOf(.4f), span = .15f to .45f),
    "ex-good" to Sketch(listOf(0f to .5f, .2f to .5f, .5f to .52f, 1f to .5f), guides = listOf(.28f, .72f), span = .2f to .5f),
    "ex-long" to Sketch(listOf(0f to .4f, .1f to .4f, .6f to .6f, .85f to .9f, 1f to .85f), guides = listOf(.7f), span = .1f to .6f),
    "ex-evening" to Sketch(listOf(0f to .4f, .55f to .4f, .8f to .75f, 1f to .9f), guides = listOf(.7f), span = .55f to .75f),
    "ex-nextday" to Sketch(emptyList(), bars = listOf(.45f, .85f)),
    "ex-intensity" to Sketch(listOf(0f to .5f, .15f to .5f, .4f to .1f, .7f to .5f, 1f to .5f), second = listOf(0f to .5f, .15f to .5f, .4f to .35f, .7f to .5f, 1f to .5f), span = .15f to .5f),
    "sleep-short" to Sketch(emptyList(), bars = listOf(.9f, .45f)),
    "sleep-restless" to Sketch(listOf(0f to .5f, .12f to .15f, .25f to .8f, .38f to .2f, .52f to .75f, .68f to .3f, .84f to .65f, 1f to .5f), second = listOf(0f to .5f, .3f to .55f, .6f to .45f, 1f to .5f)),
    "sleep-irregular" to Sketch(emptyList(), bars = listOf(.8f, .4f, .7f, .35f)),
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
    GlyphTile(tint, modifier, size) {
        Canvas(Modifier.fillMaxSize().padding(7.dp)) {
            val stroke = 2.2.dp.toPx()
            val faint = tint.copy(alpha = 0.4f)
            for (g in sketch.guides) {
                val y = g * this.size.height
                drawLine(faint, Offset(0f, y), Offset(this.size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
            }
            sketch.span?.let { (from, to) ->
                drawRect(tint.copy(alpha = 0.18f), Offset(from * this.size.width, 0f), androidx.compose.ui.geometry.Size((to - from) * this.size.width, this.size.height))
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

/** The rounded, softly tinted tile on the left of every card in the bell and the patterns list. */
@Composable
fun GlyphTile(tint: Color, modifier: Modifier = Modifier, size: Dp = 48.dp, content: @Composable () -> Unit) {
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
        content()
    }
}
