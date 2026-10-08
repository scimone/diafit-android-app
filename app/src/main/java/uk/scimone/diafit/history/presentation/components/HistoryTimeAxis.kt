package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * The single 0–24 h axis shared by every track below it, full width like the tracks. Labelled every 6 h.
 */
@Composable
fun HistoryTimeAxis(modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val style = TextStyle(color = color, fontSize = 10.sp)
    Row(modifier.fillMaxWidth().height(20.dp)) {
        Canvas(Modifier.weight(1f).height(20.dp)) {
            for (hour in 0..24 step 6) {
                val x = hour / 24f * size.width
                val label = measurer.measure("%02d:00".format(hour), style)
                drawText(label, topLeft = Offset((x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width), 0f))
            }
        }
    }
}
