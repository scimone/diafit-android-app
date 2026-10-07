package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.ActivitySpan
import uk.scimone.diafit.ui.theme.Activity
import uk.scimone.diafit.ui.theme.Sleep
import kotlin.math.max

/**
 * The day's activity on the shared 24 h scale, in three layers: sleep as a plain filled bar,
 * **elevated activity** found in heart rate and steps ([uk.scimone.diafit.core.domain.model.ElevatedActivity])
 * as soft blocks that get stronger with intensity, and logged workouts as solid capsules on top.
 * A night that crosses midnight is drawn on both days, clipped to each. Shown even when the day has
 * no other data.
 */
@Composable
fun ActivityStrip(
    data: ActivityData,
    elevated: List<ActivitySpan>,
    dayStartUtc: Long,
    dayEndUtc: Long,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val guides = hourGuideColor()
    Canvas(modifier.fillMaxWidth().height(height).background(stripBackground())) {
        drawHourGuides(guides)
        val axis = DayXAxis(dayStartUtc, dayEndUtc, size.width)
        data.sleepSessions.forEach { s ->
            val x0 = axis.x(s.startUtc).coerceAtLeast(0f)
            val x1 = axis.x(s.endUtc).coerceAtMost(size.width)
            if (x1 > x0) drawRoundRect(Sleep.copy(alpha = 0.7f), Offset(x0, 1f), Size(x1 - x0, size.height - 2f), CornerRadius(size.height / 2))
        }
        elevated.forEach { span ->
            val x0 = axis.x(span.startUtc).coerceAtLeast(0f)
            val x1 = axis.x(span.endUtc).coerceAtMost(size.width)
            if (x1 > x0) {
                drawRoundRect(
                    Activity.copy(alpha = 0.25f + 0.45f * span.intensity),
                    Offset(x0, 1f), Size(max(2f, x1 - x0), size.height - 2f), CornerRadius(size.height / 2)
                )
            }
        }
        data.exercise.forEach { ex ->
            val x0 = axis.x(ex.startUtc).coerceAtLeast(0f)
            val x1 = axis.x(ex.endUtc).coerceAtMost(size.width)
            if (x1 > x0) drawRoundRect(Activity, Offset(x0, 1f), Size(max(3f, x1 - x0), size.height - 2f), CornerRadius(size.height / 2))
        }
    }
}
