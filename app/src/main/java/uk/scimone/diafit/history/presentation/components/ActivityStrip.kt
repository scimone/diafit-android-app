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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.SleepStage
import uk.scimone.diafit.ui.theme.Activity
import uk.scimone.diafit.ui.theme.Sleep
import uk.scimone.diafit.ui.theme.SleepAwake
import uk.scimone.diafit.ui.theme.SleepDeep
import uk.scimone.diafit.ui.theme.SleepLight
import uk.scimone.diafit.ui.theme.SleepRem
import kotlin.math.max

/**
 * The day's sleep and exercise on the shared 24 h scale: sleep as a bar split into its stages
 * (deep → dark, REM → light, awake → amber), exercise as a magenta capsule. A night that crosses
 * midnight is drawn on both days, clipped to each. Shown even when the day has no other data.
 */
@Composable
fun ActivityStrip(
    data: ActivityData,
    dayStartUtc: Long,
    dayEndUtc: Long,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val guides = hourGuideColor()
    Canvas(modifier.fillMaxWidth().height(height).background(stripBackground())) {
        drawHourGuides(guides)
        val axis = DayXAxis(dayStartUtc, dayEndUtc, size.width)
        data.sleep.forEach { st ->
            val x0 = axis.x(st.startUtc).coerceAtLeast(0f)
            val x1 = axis.x(st.endUtc).coerceAtMost(size.width)
            if (x1 <= x0) return@forEach
            val color: Color = when (st.sleepStage) {
                SleepStage.AWAKE -> SleepAwake
                SleepStage.REM -> SleepRem
                SleepStage.LIGHT -> SleepLight
                SleepStage.DEEP -> SleepDeep
                SleepStage.SLEEPING -> Sleep
            }
            drawRect(color, Offset(x0, 1f), Size(max(1f, x1 - x0), size.height - 2f))
        }
        data.exercise.forEach { ex ->
            val x0 = axis.x(ex.startUtc).coerceAtLeast(0f)
            val x1 = axis.x(ex.endUtc).coerceAtMost(size.width)
            if (x1 <= x0) return@forEach
            drawRoundRect(Activity, Offset(x0, 1f), Size(max(3f, x1 - x0), size.height - 2f), CornerRadius(size.height / 2))
        }
    }
}
