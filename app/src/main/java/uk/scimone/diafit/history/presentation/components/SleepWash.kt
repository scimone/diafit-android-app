package uk.scimone.diafit.history.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * The time asleep as a grey wash behind everything drawn on top: one stretch per sleep session
 * ([sessions] as start/end UTC), clipped to the day window. A night that crosses midnight shows on both days.
 */
@Composable
fun Modifier.sleepWash(sessions: List<Pair<Long, Long>>, dayStartUtc: Long, dayEndUtc: Long): Modifier {
    if (sessions.isEmpty()) return this
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SLEEP_WASH_ALPHA)
    return drawBehind {
        val axis = DayXAxis(dayStartUtc, dayEndUtc, size.width)
        sessions.forEach { (start, end) ->
            val x0 = axis.x(start).coerceAtLeast(0f)
            val x1 = axis.x(end).coerceAtMost(size.width)
            if (x1 > x0) drawRect(color, Offset(x0, 0f), Size(x1 - x0, size.height))
        }
    }
}

/** Same wash for the period profile: [share] per bin of the day is the share (0..1) of days that were asleep then. */
@Composable
fun Modifier.sleepWashShare(share: FloatArray): Modifier {
    if (share.none { it > 0f }) return this
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    return drawBehind {
        val bin = size.width / share.size
        for (i in share.indices) if (share[i] > 0f) {
            // Whole-pixel edges, so neighbouring bins never overlap (an overlap shows as a faint stripe).
            val x0 = kotlin.math.floor(i * bin)
            val x1 = kotlin.math.floor((i + 1) * bin)
            drawRect(base.copy(alpha = SLEEP_WASH_ALPHA * 0.5f * share[i]), Offset(x0, 0f), Size(x1 - x0, size.height))
        }
    }
}

private const val SLEEP_WASH_ALPHA = 0.08f
