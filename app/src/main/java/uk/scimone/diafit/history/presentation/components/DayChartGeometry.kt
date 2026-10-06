package uk.scimone.diafit.history.presentation.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import uk.scimone.diafit.history.presentation.model.GlucosePoint

/** Readings further apart than this are a sensor gap: charts break the trace instead of bridging it. */
const val GLUCOSE_GAP_MS = 15 * 60_000L

/** Time to horizontal position within one day's window. */
class DayXAxis(private val startUtc: Long, endUtc: Long, private val widthPx: Float) {
    private val spanMs = (endUtc - startUtc).toFloat()
    fun x(timeUtc: Long): Float = (timeUtc - startUtc) / spanMs * widthPx
}

/** Splits [points] into runs of consecutive readings with no sensor gap between them. */
fun List<GlucosePoint>.splitAtGaps(maxGapMs: Long = GLUCOSE_GAP_MS): List<List<GlucosePoint>> {
    val runs = mutableListOf<MutableList<GlucosePoint>>()
    forEach { point ->
        val run = runs.lastOrNull()
        if (run != null && point.timeUtc - run.last().timeUtc <= maxGapMs) run += point
        else runs += mutableListOf(point)
    }
    return runs
}

/** Faint vertical guides at 06:00, 12:00 and 18:00, the same on every track so the eye can follow an hour down the page. */
fun DrawScope.drawHourGuides(color: Color) {
    for (hour in 6..18 step 6) {
        val x = hour / 24f * size.width
        drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f)
    }
}
