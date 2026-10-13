package uk.scimone.diafit.patterns.presentation

import uk.scimone.diafit.core.presentation.formatStamp
import uk.scimone.diafit.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.patterns.domain.PatternExample
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.Activity
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.InRange
import uk.scimone.diafit.ui.theme.Sleep
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.sqrt

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** The y range shared by every example on a page, so their levels can be compared at a glance. */
internal fun exampleYMax(data: List<ExampleData>?): Float {
    val top = data?.flatMap { it.readings }?.maxOfOrNull { it.valueMgdl } ?: 0
    return maxOf(250f, ceil(top / 50f) * 50f).coerceAtMost(400f)
}

/**
 * One real occurrence of the pattern: date, what made it an example, and the glucose of that stretch with the part the
 * pattern is about shaded, the event it is aligned on, the key moment ringed, and meals, insulin, activity and sleep
 * underneath, so the reason can often be read off directly. Tapping opens the day.
 */
@Composable
internal fun ExampleCard(
    example: PatternExample, data: ExampleData?, thresholds: GlucoseThresholds, yMax: Float, accent: Color,
    groupColor: Color?, onOpen: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    val anchor = Instant.ofEpochMilli(example.eventUtc ?: example.focusFromUtc).atZone(zone)
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = groupColor?.let { BorderStroke(1.dp, it.copy(alpha = 0.35f)) }
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val wholeDay = example.toUtc - example.fromUtc in 23 * 3_600_000L..25 * 3_600_000L &&
                        Instant.ofEpochMilli(example.fromUtc).atZone(zone).let { it.hour == 0 && it.minute == 0 }
                    Text(
                        "${formatStamp(anchor.toInstant().toEpochMilli(), "EEEdMMM")} · " + if (wholeDay) stringResource(R.string.pat_ui_whole_day)
                        else "${timeFormat.format(Instant.ofEpochMilli(example.fromUtc).atZone(zone))}–${timeFormat.format(Instant.ofEpochMilli(example.toUtc).atZone(zone))}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(example.note.text(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.pat_ui_open_day), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ExampleChart(example, data, thresholds, yMax, accent, Modifier.fillMaxWidth().padding(top = 6.dp, end = 8.dp).height(160.dp))
        }
    }
}

@Composable
private fun ExampleChart(example: PatternExample, data: ExampleData?, t: GlucoseThresholds, yMax: Float, accent: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val label = TextStyle(fontSize = 9.sp, color = muted)
    val noSensor = stringResource(R.string.pat_ui_no_sensor)
    val zone = ZoneId.systemDefault()
    Canvas(modifier) {
        val laneH = 40.dp.toPx()
        val axisH = 14.dp.toPx()
        val plotH = size.height - laneH - axisH
        val span = (example.toUtc - example.fromUtc).toFloat().coerceAtLeast(1f)
        fun x(t: Long) = size.width * (t - example.fromUtc) / span
        val yMin = 40f
        fun y(v: Float) = plotH * (1f - (v.coerceIn(yMin, yMax) - yMin) / (yMax - yMin))

        // Target range, its edges, and the part of the stretch the pattern is about.
        drawRect(InRange.copy(alpha = 0.07f), Offset(0f, y(t.high.toFloat())), Size(size.width, y(t.low.toFloat()) - y(t.high.toFloat())))
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        for (v in listOf(t.low, t.high)) drawLine(muted.copy(alpha = 0.35f), Offset(0f, y(v.toFloat())), Offset(size.width, y(v.toFloat())), 1.dp.toPx(), pathEffect = dash)
        // Not when the focus is the whole stretch (a whole-day example): shading everything says nothing.
        val fx0 = x(example.focusFromUtc).coerceIn(0f, size.width); val fx1 = x(example.focusToUtc).coerceIn(0f, size.width)
        if (fx1 - fx0 < size.width * 0.95f) {
            drawRect(accent.copy(alpha = 0.10f), Offset(fx0, 0f), Size(fx1 - fx0, plotH))
            drawRect(accent.copy(alpha = 0.7f), Offset(fx0, 0f), Size(fx1 - fx0, 2.dp.toPx()))
        }

        // Hour grid and labels.
        val hours = span / 3_600_000f
        val step = when { hours <= 6 -> 1; hours <= 12 -> 2; hours <= 18 -> 3; else -> 4 }
        var tick = Instant.ofEpochMilli(example.fromUtc).atZone(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1)
        while (tick.toInstant().toEpochMilli() < example.toUtc) {
            if (tick.hour % step == 0) {
                val tx = x(tick.toInstant().toEpochMilli())
                drawLine(muted.copy(alpha = 0.12f), Offset(tx, 0f), Offset(tx, plotH + laneH), 1.dp.toPx())
                val text = measurer.measure("%02d".format(tick.hour), label)
                if (tx - text.size.width / 2 >= 0 && tx + text.size.width / 2 <= size.width) {
                    drawText(text, topLeft = Offset(tx - text.size.width / 2, size.height - text.size.height))
                }
            }
            tick = tick.plusHours(1)
        }
        for (v in listOf(t.low, t.high)) {
            val text = measurer.measure("$v", label)
            drawText(text, topLeft = Offset(2.dp.toPx(), y(v.toFloat()) - text.size.height - 1.dp.toPx()))
        }

        // The event the pattern is aligned on (a meal, an activity, a night).
        example.eventUtc?.let { e ->
            val ex = x(e)
            val end = example.eventEndUtc?.let(::x)
            if (end != null) drawRect(muted.copy(alpha = 0.08f), Offset(ex.coerceAtLeast(0f), 0f), Size(end.coerceAtMost(size.width) - ex.coerceAtLeast(0f), plotH))
            if (ex in 0f..size.width) drawLine(onSurface.copy(alpha = 0.55f), Offset(ex, 0f), Offset(ex, plotH), 1.dp.toPx(), pathEffect = dash)
            example.eventLabel?.let { name ->
                val text = measurer.measure(name.text(), label.copy(color = onSurface, fontWeight = FontWeight.Medium))
                val lx = (ex.coerceAtLeast(0f) + 3.dp.toPx()).coerceAtMost(size.width - text.size.width)
                drawRoundRect(surface.copy(alpha = 0.85f), Offset(lx - 2.dp.toPx(), 4.dp.toPx()), Size(text.size.width + 4.dp.toPx(), text.size.height.toFloat()), CornerRadius(3.dp.toPx()))
                drawText(text, topLeft = Offset(lx, 4.dp.toPx()))
            }
        }

        if (data == null) return@Canvas
        val readings = data.readings
        if (readings.isEmpty()) {
            val text = measurer.measure(noSensor, label)
            drawText(text, topLeft = Offset((size.width - text.size.width) / 2, plotH / 2))
        }
        // Glucose, coloured by zone, broken where the sensor had a gap.
        val stroke = 2.2.dp.toPx()
        for (i in 1 until readings.size) {
            val a = readings[i - 1]; val b = readings[i]
            if (b.timestamp - a.timestamp > 15 * 60_000L) continue
            val mid = (a.valueMgdl + b.valueMgdl) / 2
            val color = when { mid < t.low -> BelowRange; mid > t.high -> AboveRange; else -> InRange }
            drawLine(color, Offset(x(a.timestamp), y(a.valueMgdl.toFloat())), Offset(x(b.timestamp), y(b.valueMgdl.toFloat())), stroke, cap = StrokeCap.Round)
        }
        // The key moment: the reading nearest to it, ringed and labelled.
        example.markUtc?.let { m ->
            val r = readings.minByOrNull { kotlin.math.abs(it.timestamp - m) }?.takeIf { kotlin.math.abs(it.timestamp - m) <= 15 * 60_000L } ?: return@let
            val c = Offset(x(r.timestamp), y(r.valueMgdl.toFloat()))
            drawCircle(surface, 5.dp.toPx(), c)
            drawCircle(onSurface, 5.dp.toPx(), c, style = Stroke(1.6.dp.toPx()))
            val text = measurer.measure("${r.valueMgdl}", label.copy(color = onSurface, fontWeight = FontWeight.Bold))
            val above = c.y - text.size.height - 7.dp.toPx() >= 0
            val ty = if (above) c.y - text.size.height - 6.dp.toPx() else c.y + 6.dp.toPx()
            drawText(text, topLeft = Offset((c.x - text.size.width / 2).coerceIn(0f, size.width - text.size.width), ty))
        }
        drawLane(data, example, ::x, plotH, laneH, measurer, label)
    }
}

/** Under the glucose: sleep and workouts as bars, meals (carbs) and insulin doses as markers with their amounts. */
private fun DrawScope.drawLane(
    data: ExampleData, example: PatternExample, x: (Long) -> Float, top: Float, height: Float, measurer: TextMeasurer, label: TextStyle
) {
    val barY = top + height - 8.dp.toPx()
    val barH = 6.dp.toPx()
    fun bar(from: Long, to: Long, color: Color) {
        val x0 = x(maxOf(from, example.fromUtc)); val x1 = x(minOf(to, example.toUtc))
        if (x1 > x0) drawRoundRect(color, Offset(x0, barY), Size(x1 - x0, barH), CornerRadius(barH / 2))
    }
    data.sleep.forEach { bar(it.startUtc, it.endUtc, Sleep.copy(alpha = 0.55f)) }
    data.workouts.forEach { bar(it.startUtc, it.endUtc, Activity.copy(alpha = 0.8f)) }

    // Carbs on one row, insulin on the row under it; a label is only drawn where it fits before the next marker.
    val carbY = top + 7.dp.toPx()
    val doseY = top + 19.dp.toPx()
    fun row(items: List<Pair<Long, String>>, color: Color, y: Float, draw: (Offset, Int) -> Unit) {
        val xs = items.map { x(it.first) }
        var lastEnd = -1f
        items.forEachIndexed { i, (_, text) ->
            val cx = xs[i]
            if (cx < 0 || cx > size.width) return@forEachIndexed
            draw(Offset(cx, y), i)
            val m = measurer.measure(text, label.copy(color = color, fontWeight = FontWeight.SemiBold))
            val lx = cx + 6.dp.toPx()
            val next = xs.getOrNull(i + 1) ?: Float.MAX_VALUE
            if (lx > lastEnd && lx + m.size.width + 4.dp.toPx() < next && lx + m.size.width <= size.width) {
                drawText(m, topLeft = Offset(lx, y - m.size.height / 2))
                lastEnd = lx + m.size.width
            }
        }
    }
    val meals = data.meals.sortedBy { it.startTime }
    row(meals.map { it.startTime to "${it.totalCarbs}g" }, Carbs, carbY) { c, i ->
        drawCircle(Carbs, (2.5f + sqrt(meals[i].totalCarbs.toFloat()) * 0.5f).coerceAtMost(5.5f).dp.toPx(), c)
    }
    val doses = data.boluses.filter { !it.isSmb }.sortedBy { it.timestampUtc }
    row(doses.map { it.timestampUtc to "%.1fU".format(it.value) }, Bolus, doseY) { c, _ ->
        val s = 4.dp.toPx()
        drawPath(Path().apply { moveTo(c.x - s, c.y - s); lineTo(c.x + s, c.y - s); lineTo(c.x, c.y + s); close() }, Bolus)
    }
    // Automatic micro-boluses: small ticks only, there are too many to label.
    data.boluses.filter { it.isSmb }.forEach {
        val cx = x(it.timestampUtc)
        if (cx in 0f..size.width) drawLine(Bolus.copy(alpha = 0.6f), Offset(cx, doseY + 6.dp.toPx()), Offset(cx, doseY + 9.dp.toPx()), 1.5.dp.toPx())
    }
}

/** The small legend explaining the example charts, once above them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExampleLegend(accent: Color) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendSwatch(accent.copy(alpha = 0.25f), stringResource(R.string.pat_ui_leg_window))
        LegendDot(Carbs, stringResource(R.string.pat_ui_leg_carbs))
        LegendTriangle(Bolus, stringResource(R.string.pat_ui_leg_insulin))
        LegendSwatch(Activity.copy(alpha = 0.8f), stringResource(R.string.pat_ui_leg_activity))
        LegendSwatch(Sleep.copy(alpha = 0.55f), stringResource(R.string.pat_ui_leg_sleep))
    }
}

@Composable
private fun LegendSwatch(color: Color, text: String) = LegendItem(text) {
    Canvas(Modifier.size(width = 12.dp, height = 8.dp)) { drawRoundRect(color, cornerRadius = CornerRadius(2.dp.toPx())) }
}

@Composable
private fun LegendDot(color: Color, text: String) = LegendItem(text) { Canvas(Modifier.size(8.dp)) { drawCircle(color) } }

@Composable
private fun LegendTriangle(color: Color, text: String) = LegendItem(text) {
    Canvas(Modifier.size(8.dp)) { drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, color) }
}

@Composable
private fun LegendItem(text: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Text(text, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
