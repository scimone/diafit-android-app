package uk.scimone.diafit.journal.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.core.domain.usecase.GlucoseResponse
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.InRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val GAP_MS = 15 * 60_000L

/**
 * Glucose around one journal event: the CGM trace coloured by range, the target band, the span the
 * event is expected to influence, the event itself, insulin, and a scrub readout. Drag across it to
 * read exact values.
 */
@Composable
fun GlucoseResponseChart(
    response: GlucoseResponse,
    eventTimeUtc: Long,
    effectEndUtc: Long,
    target: GlucoseTargetRange,
    eventColor: Color,
    modifier: Modifier = Modifier,
    nowUtc: Long = System.currentTimeMillis()
) {
    val readings = response.readings
    if (readings.isEmpty()) {
        Box(modifier.height(180.dp), contentAlignment = Alignment.Center) {
            Text(
                "No glucose readings for this period",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var scrubX by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableStateOf(1f) }

    val start = response.windowStartUtc
    // Don't plot empty future beyond "now + a little" for a meal that is still being absorbed.
    val end = max(response.windowEndUtc, readings.last().timestamp)
    val span = (end - start).toFloat()

    val scrubbed: CgmEntity? = scrubX?.let { x ->
        val t = start + (x / widthPx * span).toLong()
        readings.minByOrNull { abs(it.timestamp - t) }
    }

    Column(modifier) {
        ScrubReadout(scrubbed, eventTimeUtc)
        Spacer(Modifier.height(8.dp))

        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        val gridColor = MaterialTheme.colorScheme.outlineVariant
        val textMeasurer = rememberTextMeasurer()
        val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
        val lineColor = MaterialTheme.colorScheme.onSurface

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(Unit) { detectTapGestures(onPress = { scrubX = it.x; tryAwaitRelease(); scrubX = null }) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { scrubX = it.x },
                        onHorizontalDrag = { change, _ -> scrubX = change.position.x },
                        onDragEnd = { scrubX = null },
                        onDragCancel = { scrubX = null }
                    )
                }
        ) {
            widthPx = size.width
            val axisH = 22.dp.toPx()
            val plotH = size.height - axisH
            val rawMin = min(readings.minOf { it.valueMgdl }, target.lowerBound)
            val rawMax = max(readings.maxOf { it.valueMgdl }, target.upperBound)
            val yMin = (floor((rawMin - 10) / 10f) * 10f).coerceAtLeast(0f)
            val yMax = ceil((rawMax + 10) / 10f) * 10f
            fun xOf(t: Long) = (t - start) / span * size.width
            fun yOf(v: Float) = plotH - (v - yMin) / (yMax - yMin) * plotH

            // Target band
            drawRect(
                InRange.copy(alpha = 0.10f),
                topLeft = Offset(0f, yOf(target.upperBound.toFloat())),
                size = Size(size.width, yOf(target.lowerBound.toFloat()) - yOf(target.upperBound.toFloat()))
            )
            // Window the event is expected to influence
            val effX0 = xOf(eventTimeUtc)
            val effX1 = xOf(min(effectEndUtc, end))
            drawRect(eventColor.copy(alpha = 0.08f), Offset(effX0, 0f), Size(effX1 - effX0, plotH))

            // Y guides at the target bounds
            listOf(target.lowerBound, target.upperBound).forEach { v ->
                val y = yOf(v.toFloat())
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
                drawText(textMeasurer, "$v", Offset(4.dp.toPx(), y - 13.dp.toPx()), labelStyle)
            }

            // X labels
            val stepMs = if (span > 6 * 3_600_000L) 2 * 3_600_000L else 3_600_000L
            var tick = ceil(start / stepMs.toDouble()).toLong() * stepMs
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            while (tick <= end) {
                val x = xOf(tick)
                drawLine(gridColor.copy(alpha = 0.5f), Offset(x, plotH), Offset(x, plotH + 4.dp.toPx()), 1.dp.toPx())
                val label = textMeasurer.measure(fmt.format(Date(tick)), labelStyle)
                val lx = (x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                drawText(label, topLeft = Offset(lx, plotH + 6.dp.toPx()))
                tick += stepMs
            }

            // CGM trace, one coloured segment per pair of readings (broken across data gaps)
            val stroke = 3.dp.toPx()
            readings.zipWithNext().forEach { (a, b) ->
                if (b.timestamp - a.timestamp > GAP_MS) return@forEach
                val avg = (a.valueMgdl + b.valueMgdl) / 2f
                val c = when {
                    avg > target.upperBound -> AboveRange
                    avg < target.lowerBound -> BelowRange
                    else -> InRange
                }
                drawLine(c, Offset(xOf(a.timestamp), yOf(a.valueMgdl.toFloat())),
                    Offset(xOf(b.timestamp), yOf(b.valueMgdl.toFloat())), stroke, StrokeCap.Round)
            }

            // The event itself
            drawLine(eventColor, Offset(effX0, 0f), Offset(effX0, plotH), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            drawCircle(eventColor, 6.dp.toPx(), Offset(effX0, 8.dp.toPx()))

            // Insulin: small triangles on the baseline, size grows with the dose
            response.boluses.forEach { b ->
                val x = xOf(b.timestampUtc)
                val h = (6f + min(b.value, 10f) * 1.6f).dp.toPx()
                val w = h * 0.8f
                val p = Path().apply {
                    moveTo(x, plotH - h); lineTo(x - w / 2, plotH); lineTo(x + w / 2, plotH); close()
                }
                drawPath(p, Bolus.copy(alpha = if (b.isSmb) 0.55f else 0.95f))
            }

            // "Now"
            if (nowUtc in start..end) {
                val x = xOf(nowUtc)
                drawLine(labelColor, Offset(x, 0f), Offset(x, plotH), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f)))
            }

            // Scrub cursor
            scrubbed?.let { r ->
                val x = xOf(r.timestamp)
                drawLine(lineColor.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, plotH), 1.dp.toPx())
                drawCircle(lineColor, 5.dp.toPx(), Offset(x, yOf(r.valueMgdl.toFloat())))
            }
        }
    }
}

@Composable
private fun ScrubReadout(reading: CgmEntity?, eventTimeUtc: Long) {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        if (reading == null) {
            Text(
                "Drag across the graph for exact values",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reading.timestamp))
            Text("${reading.valueMgdl} mg/dL", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            Text(
                "$time · ${relativeToEvent(reading.timestamp - eventTimeUtc)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "+1 h 05 min after" / "20 min before" / "at meal". */
fun relativeToEvent(deltaMs: Long): String {
    val minutes = abs(deltaMs) / 60_000
    if (minutes < 2) return "at the event"
    val text = if (minutes >= 60) "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min" else "$minutes min"
    return if (deltaMs > 0) "$text after" else "$text before"
}
