package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.ActivitySpan
import uk.scimone.diafit.ui.theme.Activity
import kotlin.math.max

val ACTIVITY_LANE_HEIGHT = TREATMENT_BAND_HEIGHT

/** Raised activity as a faint wash behind the whole tile (stronger with more intensity), workouts a little stronger. */
@Composable
fun Modifier.activityWash(elevated: List<ActivitySpan>, workouts: List<ActivitySpan>, dayStartUtc: Long, dayEndUtc: Long): Modifier {
    if (elevated.isEmpty() && workouts.isEmpty()) return this
    return drawBehind {
        val axis = DayXAxis(dayStartUtc, dayEndUtc, size.width)
        fun wash(span: ActivitySpan, alpha: Float) {
            val x0 = axis.x(span.startUtc).coerceAtLeast(0f)
            val x1 = axis.x(span.endUtc).coerceAtMost(size.width)
            if (x1 > x0) drawRect(Activity.copy(alpha = alpha), Offset(x0, 0f), Size(x1 - x0, size.height))
        }
        elevated.forEach { wash(it, 0.08f + 0.10f * it.intensity) }
        workouts.forEach { wash(it, 0.22f) }
    }
}

/**
 * How active the day was, on its own lane under the treatments. Every stretch of raised activity found in
 * heart rate and steps ([elevated]) is a bar of the full lane height: the harder it was (higher heart rate, more steps),
 * the stronger its colour. A logged workout ([workouts]) is drawn almost opaque. Sleep is not drawn here
 * (it is the grey wash behind the tile).
 */
@Composable
fun ActivityLane(
    elevated: List<ActivitySpan>,
    workouts: List<ActivitySpan>,
    dayStartUtc: Long,
    dayEndUtc: Long,
    modifier: Modifier = Modifier,
    /** Multiplies the opacity (several days drawn in one lane build up). */
    alphaScale: Float = 1f
) {
    val guides = hourGuideColor()
    val floor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Canvas(modifier.fillMaxWidth().height(ACTIVITY_LANE_HEIGHT)) {
        drawHourGuides(guides)
        drawLine(floor, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
        val axis = DayXAxis(dayStartUtc, dayEndUtc, size.width)
        val corner = CornerRadius(minOf(size.height / 2, 3.dp.toPx()))
        fun bar(span: ActivitySpan, alpha: Float) {
            val x0 = axis.x(span.startUtc).coerceAtLeast(0f)
            val x1 = axis.x(span.endUtc).coerceAtMost(size.width)
            if (x1 <= x0) return
            drawRoundRect(Activity.copy(alpha = alpha.coerceIn(0f, 1f)), Offset(x0, 0f), Size(max(2.5.dp.toPx(), x1 - x0), size.height), corner)
        }
        elevated.forEach { bar(it, (0.3f + 0.5f * it.intensity) * alphaScale) }
        workouts.forEach { bar(it, 0.9f * (0.4f + 0.6f * alphaScale)) }
    }
}
