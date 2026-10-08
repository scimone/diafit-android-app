package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.ActivitySpan
import uk.scimone.diafit.ui.theme.Activity
import kotlin.math.max

val ACTIVITY_LANE_HEIGHT = 10.dp
private const val MIN_BAR_FRACTION = 0.35f

/**
 * How active the day was, on its own lane under the treatments. Every stretch of raised activity found in
 * heart rate and steps ([elevated]) is a bar standing on the lane's floor: the harder it was (higher heart rate, more steps),
 * the taller and stronger it is. A logged workout ([workouts]) fills the whole lane height. Sleep is not drawn here
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
        fun bar(span: ActivitySpan, heightFraction: Float, alpha: Float) {
            val x0 = axis.x(span.startUtc).coerceAtLeast(0f)
            val x1 = axis.x(span.endUtc).coerceAtMost(size.width)
            if (x1 <= x0) return
            val h = size.height * heightFraction
            drawRoundRect(Activity.copy(alpha = alpha.coerceIn(0f, 1f)), Offset(x0, size.height - h), Size(max(2.5.dp.toPx(), x1 - x0), h), corner)
        }
        elevated.forEach { bar(it, MIN_BAR_FRACTION + (1f - MIN_BAR_FRACTION) * it.intensity, (0.3f + 0.5f * it.intensity) * alphaScale) }
        workouts.forEach { bar(it, 1f, 0.9f * (0.4f + 0.6f * alphaScale)) }
    }
}
