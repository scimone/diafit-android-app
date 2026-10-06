package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val TICK_STEP_HOURS = 3

/** The single 0:00–24:00 axis shared by every track above it. */
@Composable
fun HistoryTimeAxis(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
    val tickColor = MaterialTheme.colorScheme.outlineVariant
    Box(modifier.fillMaxWidth().height(22.dp).padding(start = TrackLabelWidth)) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            for (hour in 0..24 step TICK_STEP_HOURS) {
                val x = hour / 24f * size.width
                drawLine(tickColor, Offset(x, 0f), Offset(x, 4.dp.toPx()), 1.dp.toPx())
                val label = measurer.measure("$hour:00", style)
                drawText(label, topLeft = Offset((x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width), 6.dp.toPx()))
            }
        }
    }
}
