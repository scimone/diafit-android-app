package uk.scimone.diafit.profile.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.scimone.diafit.core.domain.model.AapsProfile
import uk.scimone.diafit.core.domain.model.ProfileStep
import uk.scimone.diafit.core.domain.model.valueAt

/**
 * A 24 h step chart of one daily schedule (basal, ISF, carb ratio): filled steps and a dashed marker with a
 * dot at the current time of day. Touch or drag on it to scrub: a solid line follows the finger and a tooltip
 * gives the time and the exact value (with [unit]); it stays after release, tap on the line again to dismiss.
 * With [previous] the older schedule is drawn as a grey dashed line and the tooltip shows both values.
 */
@Composable
fun StepChart(
    steps: List<ProfileStep>,
    color: Color,
    nowSecond: Int,
    format: (Double) -> String,
    unit: String,
    modifier: Modifier = Modifier,
    previous: List<ProfileStep>? = null,
    height: Dp = 124.dp
) {
    if (steps.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tipStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val tipBg = MaterialTheme.colorScheme.inverseSurface
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val scrubColor = MaterialTheme.colorScheme.onSurface
    val previousColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    val sorted = steps.sortedBy { it.startSeconds }
    val sortedPrevious = previous?.sortedBy { it.startSeconds }?.takeIf { it.isNotEmpty() }
    val maxValue = (maxOf(sorted.maxOf { it.value }, sortedPrevious?.maxOf { it.value } ?: 0.0) * 1.25).coerceAtLeast(0.001)
    // The scrub position as a fraction of the day (null = none).
    var scrub by remember { mutableStateOf<Float?>(null) }

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        val current = scrub
                        // Tapping the pinned line again dismisses it.
                        scrub = if (current != null && kotlin.math.abs(current - f) * size.width < 24.dp.toPx()) null else f
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> scrub = (offset.x / size.width).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ -> scrub = (change.position.x / size.width).coerceIn(0f, 1f) }
                    )
                }
        ) {
            val day = AapsProfile.SECONDS_PER_DAY.toFloat()
            val topPad = 34.dp.toPx()  // room for the tooltip above the plot
            val plotH = size.height - topPad
            fun x(sec: Int) = sec / day * size.width
            fun y(v: Double) = topPad + plotH * (1f - (v / maxValue).toFloat())

            // 6-hourly guides.
            for (h in 0..24 step 6) {
                val gx = x(h * 3600)
                drawLine(grid, Offset(gx, topPad), Offset(gx, size.height), strokeWidth = 1.dp.toPx())
            }

            fun stepPath(list: List<ProfileStep>, closeFill: Boolean): Path {
                val path = Path()
                list.forEachIndexed { i, s ->
                    val end = list.getOrNull(i + 1)?.startSeconds ?: AapsProfile.SECONDS_PER_DAY
                    val x0 = x(s.startSeconds)
                    val yv = y(s.value)
                    if (i == 0) {
                        if (closeFill) { path.moveTo(x0, size.height); path.lineTo(x0, yv) } else path.moveTo(x0, yv)
                    } else path.lineTo(x0, yv)
                    path.lineTo(x(end), yv)
                }
                if (closeFill) { path.lineTo(size.width, size.height); path.close() }
                return path
            }

            drawPath(stepPath(sorted, closeFill = true), color.copy(alpha = 0.18f))
            sortedPrevious?.let {
                drawPath(
                    stepPath(it, closeFill = false), previousColor,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
            }
            drawPath(stepPath(sorted, closeFill = false), color, style = Stroke(width = 2.dp.toPx()))

            // Now marker.
            val nx = x(nowSecond.coerceIn(0, AapsProfile.SECONDS_PER_DAY))
            drawLine(
                color, Offset(nx, topPad), Offset(nx, size.height),
                strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
            val nowValue = sorted.valueAt(nowSecond) ?: sorted.last().value
            drawCircle(Color.White, 5.dp.toPx(), Offset(nx, y(nowValue)))
            drawCircle(color, 3.5.dp.toPx(), Offset(nx, y(nowValue)))

            // Scrub line + tooltip.
            scrub?.let { f ->
                val sec = (f * AapsProfile.SECONDS_PER_DAY).toInt().coerceIn(0, AapsProfile.SECONDS_PER_DAY - 1) / 60 * 60
                val value = sorted.valueAt(sec) ?: sorted.last().value
                val before = sortedPrevious?.valueAt(sec)
                val sx = x(sec)
                drawLine(scrubColor.copy(alpha = 0.7f), Offset(sx, topPad - 6.dp.toPx()), Offset(sx, size.height), strokeWidth = 1.5.dp.toPx())
                drawCircle(Color.White, 5.5.dp.toPx(), Offset(sx, y(value)))
                drawCircle(color, 4.dp.toPx(), Offset(sx, y(value)))
                before?.let { drawCircle(previousColor, 3.dp.toPx(), Offset(sx, y(it))) }

                val text = buildString {
                    append("%02d:%02d".format(sec / 3600, sec / 60 % 60)).append("  ·  ")
                    if (before != null && kotlin.math.abs(before - value) > 1e-6) append(format(before)).append(" → ")
                    append(format(value)).append(' ').append(unit)
                }
                val layout = measurer.measure(text, tipStyle)
                val padX = 8.dp.toPx()
                val padY = 4.dp.toPx()
                val w = layout.size.width + 2 * padX
                val h = layout.size.height + 2 * padY
                val left = (sx - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
                drawRoundRect(tipBg, Offset(left, 0f), Size(w, h), CornerRadius(8.dp.toPx()))
                drawText(layout, topLeft = Offset(left + padX, padY))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            listOf("00", "06", "12", "18", "24").forEachIndexed { i, label ->
                Text(
                    label,
                    Modifier.weight(if (i == 0 || i == 4) 0.5f else 1f),
                    style = labelStyle,
                    textAlign = when (i) {
                        0 -> TextAlign.Start
                        4 -> TextAlign.End
                        else -> TextAlign.Center
                    }
                )
            }
        }
    }
}
