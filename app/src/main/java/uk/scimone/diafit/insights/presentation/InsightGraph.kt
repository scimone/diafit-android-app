package uk.scimone.diafit.insights.presentation

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
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.presentation.AgpBands
import uk.scimone.diafit.history.presentation.AgpPlot
import uk.scimone.diafit.insights.domain.MiniGraph

/**
 * The visualization of an insight, drawn from the stored observations. [large] adds y labels and hour labels for
 * the detail page; the card version stays minimal.
 */
@Composable
internal fun InsightGraph(graph: MiniGraph, thresholds: GlucoseThresholds, large: Boolean, modifier: Modifier = Modifier) {
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
                    highlights = graph.highlights.map { it.startHour to it.endHour },
                    yLabels = large, smooth = false
                )
            }
            is MiniGraph.ProfileComparison -> ComparisonPlot(graph, thresholds, large, Modifier.fillMaxWidth().height(height))
        }
        HourLabels(Modifier.fillMaxWidth().padding(top = 2.dp))
        if (graph is MiniGraph.ProfileComparison) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(MaterialTheme.colorScheme.primary, graph.aLabel)
                Legend(MaterialTheme.colorScheme.onSurfaceVariant, graph.bLabel)
            }
        } else if (large) {
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
