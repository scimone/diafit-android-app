package uk.scimone.diafit.patterns.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.presentation.AgpBands
import uk.scimone.diafit.history.presentation.AgpPlot
import uk.scimone.diafit.patterns.domain.MiniGraph

/**
 * The visualization of an pattern, drawn from the stored observations. [large] adds y labels and hour labels for
 * the detail page; the card version stays minimal.
 */
@Composable
internal fun PatternGraph(
    graph: MiniGraph, thresholds: GlucoseThresholds, large: Boolean, modifier: Modifier = Modifier,
    highlightColor: Color = MaterialTheme.colorScheme.primary
) {
    val height = if (large) 220.dp else 96.dp
    Column(modifier) {
        when (graph) {
            is MiniGraph.AgpWindow -> {
                val bands = remember(graph) {
                    fun List<Int>.f() = FloatArray(size) { this[it].toFloat() }
                    AgpBands(graph.p10.f(), graph.p25.f(), graph.p50.f(), graph.p75.f(), graph.p90.f())
                }
                AgpPlot(
                    bands, thresholds, Modifier.fillMaxWidth().height(height),
                    highlights = graph.highlights.map { it.startHour to it.endHour }, highlightColor = highlightColor,
                    yLabels = large, smooth = false
                )
            }
            is MiniGraph.ProfileComparison -> ComparisonPlot(graph, thresholds, large, Modifier.fillMaxWidth().height(height))
            is MiniGraph.EventResponse -> MealPlot(graph, large, Modifier.fillMaxWidth().height(height))
        }
        if (graph is MiniGraph.EventResponse) MealLabels(graph, Modifier.fillMaxWidth().padding(top = 2.dp))
        else HourLabels(Modifier.fillMaxWidth().padding(top = 2.dp))
        if (graph is MiniGraph.ProfileComparison) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(MaterialTheme.colorScheme.primary, graph.aLabel)
                Legend(MaterialTheme.colorScheme.onSurfaceVariant, graph.bLabel)
            }
        } else if (graph is MiniGraph.EventResponse) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(MaterialTheme.colorScheme.primary, graph.aLabel)
                graph.bLabel?.let { Legend(MaterialTheme.colorScheme.onSurfaceVariant, it) }
            }
            if (large) Text(
                "Change in glucose from the starting level of each ${if (graph.eventLabel == "meal") "meal" else "session"}; line = typical, band = middle half",
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (graph is MiniGraph.AgpWindow && large) {
            Text(
                "Median with 25–75 % and 10–90 % ranges, by time of day",
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 14.dp, height = 3.dp)) { drawRect(color) }
        Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ComparisonPlot(graph: MiniGraph.ProfileComparison, thresholds: GlucoseThresholds, large: Boolean, modifier: Modifier) {
    val a = MaterialTheme.colorScheme.primary
    val b = MaterialTheme.colorScheme.onSurfaceVariant
    val band = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Canvas(modifier) {
        val top = graph.a.plus(graph.b).max().toFloat()
        val yMax = maxOf(250f, kotlin.math.ceil(top / 50f) * 50f)
        val yMin = 40f
        fun y(v: Float) = size.height * (1f - (v.coerceIn(yMin, yMax) - yMin) / (yMax - yMin))
        drawRect(band, Offset(0f, y(thresholds.high.toFloat())), Size(size.width, y(thresholds.low.toFloat()) - y(thresholds.high.toFloat())))
        fun line(values: List<Int>, color: Color, width: Float) {
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = size.width * (i + 0.5f) / values.size
                if (i == 0) path.moveTo(x, y(v.toFloat())) else path.lineTo(x, y(v.toFloat()))
            }
            drawPath(path, color, style = Stroke(width = width))
        }
        val w = if (large) 3.dp.toPx() else 2.dp.toPx()
        line(graph.b, b, w)
        line(graph.a, a, w)
    }
}

/** 00, 06, 12, 18, 24 under the plot. */
@Composable
private fun HourLabels(modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        for (h in listOf("00", "06", "12", "18", "24")) {
            Text(h, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MealPlot(graph: MiniGraph.EventResponse, large: Boolean, modifier: Modifier) {
    val a = MaterialTheme.colorScheme.primary
    val b = MaterialTheme.colorScheme.onSurfaceVariant
    val guide = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp), color = b)
    Canvas(modifier) {
        val n = graph.median.size
        val all = graph.p25 + graph.p75 + graph.median + (graph.compare ?: emptyList())
        val yMax = maxOf(60f, kotlin.math.ceil(all.max() / 20f) * 20f)
        val yMin = minOf(-30f, kotlin.math.floor(all.min() / 20f) * 20f)
        fun y(v: Int) = size.height * (1f - (v.toFloat().coerceIn(yMin, yMax) - yMin) / (yMax - yMin))
        fun x(i: Int) = size.width * i / (n - 1)
        // The starting level (zero) and the meal itself.
        drawLine(guide, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        val mealX = x(-graph.startMinute / graph.stepMinutes)
        graph.spanMinutes?.let { span ->
            val end = size.width * (-graph.startMinute + span).toFloat() / graph.stepMinutes / (n - 1)
            drawRect(a.copy(alpha = 0.10f), Offset(mealX, 0f), Size((end - mealX).coerceAtLeast(0f), size.height))
        }
        drawLine(guide, Offset(mealX, 0f), Offset(mealX, size.height), strokeWidth = 1.dp.toPx())
        val band = Path().apply {
            graph.p75.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
            for (i in n - 1 downTo 0) lineTo(x(i), y(graph.p25[i]))
            close()
        }
        drawPath(band, a.copy(alpha = 0.18f))
        fun line(values: List<Int>, color: Color, w: Float, dashed: Boolean) {
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(
                path, color,
                style = Stroke(width = w, pathEffect = if (dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null)
            )
        }
        val w = if (large) 3.dp.toPx() else 2.dp.toPx()
        graph.compare?.let { line(it, b, w, dashed = true) }
        line(graph.median, a, w, dashed = false)
        if (large) {
            // Change in mg/dL from the starting level: the top, zero and the bottom of the scale.
            for (v in listOf(yMax.toInt(), 0, yMin.toInt())) {
                val text = measurer.measure(if (v > 0) "+$v" else if (v < 0) "−${-v}" else "start", labelStyle)
                val ty = (y(v) - text.size.height / 2).coerceIn(0f, size.height - text.size.height)
                drawText(text, topLeft = Offset(size.width - text.size.width, ty))
            }
        }
    }
}

/** The labelled minutes under a [MiniGraph.EventResponse]: the event itself at 0, then whole hours. */
@Composable
private fun MealLabels(graph: MiniGraph.EventResponse, modifier: Modifier) {
    val span = (graph.median.size - 1) * graph.stepMinutes
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.height(14.dp)) {
        for (minute in graph.axis) {
            val frac = (minute - graph.startMinute).toFloat() / span
            Text(
                if (minute == 0) graph.eventLabel else "${minute / 60}h",
                Modifier.padding(start = (maxWidth * frac - 10.dp).coerceIn(0.dp, maxWidth - 24.dp)),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
