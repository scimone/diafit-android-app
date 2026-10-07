package uk.scimone.diafit.profile.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.scimone.diafit.core.domain.model.AapsProfile
import uk.scimone.diafit.core.domain.model.ProfileStep

/**
 * A 24 h step chart of one daily schedule (basal, ISF, carb ratio): filled steps, the value written on
 * every step wide enough to hold it, and a dashed marker with a dot at the current time of day.
 */
@Composable
fun StepChart(
    steps: List<ProfileStep>,
    color: Color,
    nowSecond: Int,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 104.dp
) {
    if (steps.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val sorted = steps.sortedBy { it.startSeconds }
    val maxValue = (sorted.maxOf { it.value } * 1.25).coerceAtLeast(0.001)

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val day = AapsProfile.SECONDS_PER_DAY.toFloat()
            val topPad = 14.dp.toPx()
            val plotH = size.height - topPad
            fun x(sec: Int) = sec / day * size.width
            fun y(v: Double) = topPad + plotH * (1f - (v / maxValue).toFloat())

            // 6-hourly guides.
            for (h in 0..24 step 6) {
                val gx = x(h * 3600)
                drawLine(grid, Offset(gx, topPad), Offset(gx, size.height), strokeWidth = 1.dp.toPx())
            }

            val fill = Path()
            val line = Path()
            sorted.forEachIndexed { i, s ->
                val end = sorted.getOrNull(i + 1)?.startSeconds ?: AapsProfile.SECONDS_PER_DAY
                val x0 = x(s.startSeconds)
                val x1 = x(end)
                val yv = y(s.value)
                if (i == 0) {
                    fill.moveTo(x0, size.height); fill.lineTo(x0, yv); line.moveTo(x0, yv)
                } else {
                    fill.lineTo(x0, yv); line.lineTo(x0, yv)
                }
                fill.lineTo(x1, yv); line.lineTo(x1, yv)
            }
            fill.lineTo(size.width, size.height); fill.close()
            drawPath(fill, color.copy(alpha = 0.18f))
            drawPath(line, color, style = Stroke(width = 2.dp.toPx()))

            // Value on every step that is wide enough.
            sorted.forEachIndexed { i, s ->
                val end = sorted.getOrNull(i + 1)?.startSeconds ?: AapsProfile.SECONDS_PER_DAY
                val text = measurer.measure(format(s.value), valueStyle)
                val w = x(end) - x(s.startSeconds)
                if (w >= text.size.width + 6.dp.toPx()) {
                    drawText(text, topLeft = Offset(x(s.startSeconds) + (w - text.size.width) / 2, y(s.value) - text.size.height - 1.dp.toPx()))
                }
            }

            // Now marker.
            val nx = x(nowSecond.coerceIn(0, AapsProfile.SECONDS_PER_DAY))
            drawLine(
                color, Offset(nx, topPad), Offset(nx, size.height),
                strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
            val nowValue = sorted.lastOrNull { it.startSeconds <= nowSecond }?.value ?: sorted.last().value
            drawCircle(Color.White, 5.dp.toPx(), Offset(nx, y(nowValue)))
            drawCircle(color, 3.5.dp.toPx(), Offset(nx, y(nowValue)))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            listOf("00", "06", "12", "18", "24").forEachIndexed { i, label ->
                Text(
                    label,
                    Modifier.weight(if (i == 0 || i == 4) 0.5f else 1f),
                    style = labelStyle,
                    textAlign = when (i) {
                        0 -> androidx.compose.ui.text.style.TextAlign.Start
                        4 -> androidx.compose.ui.text.style.TextAlign.End
                        else -> androidx.compose.ui.text.style.TextAlign.Center
                    }
                )
            }
        }
    }
}
