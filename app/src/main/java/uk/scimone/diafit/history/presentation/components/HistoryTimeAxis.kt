package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The single 0–24 h axis shared by every track below it, inset like the tracks (date column on the
 * left, time-in-range column on the right). Labelled every 6 h with minor ticks every 3 h.
 */
@Composable
fun HistoryTimeAxis(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val style = TextStyle(color = color, fontSize = 10.sp)
    Row(modifier.fillMaxWidth().height(20.dp)) {
        Spacer(Modifier.width(DAY_LABEL_WIDTH))
        Canvas(Modifier.weight(1f).height(20.dp)) {
            for (hour in 0..24 step 3) {
                val x = hour / 24f * size.width
                if (hour % 6 == 0) {
                    val label = measurer.measure("%02d:00".format(hour), style)
                    drawText(label, topLeft = Offset((x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width), 0f))
                } else {
                    drawLine(color.copy(alpha = 0.4f), Offset(x, size.height - 8.dp.toPx()), Offset(x, size.height - 4.dp.toPx()), 1.dp.toPx())
                }
            }
        }
        Spacer(Modifier.width(DAY_SUMMARY_WIDTH))
    }
}
