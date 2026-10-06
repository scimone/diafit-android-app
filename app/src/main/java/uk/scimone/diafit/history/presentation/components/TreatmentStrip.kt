package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.history.domain.model.TreatmentCluster
import kotlin.math.min

private const val BLOCK_HALF_WIDTH_MS = 30 * 60_000L

/**
 * One row of treatment blocks along the day: each cluster is a one-hour block centred on the
 * sitting, whose opacity grows with its total up to [fullIntensityAt].
 */
@Composable
fun TreatmentStrip(
    clusters: List<TreatmentCluster>,
    dayStartUtc: Long,
    dayEndUtc: Long,
    color: Color,
    fullIntensityAt: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val axis = DayXAxis(dayStartUtc, dayEndUtc, size.width)
        clusters.forEach { cluster ->
            val x0 = axis.x(cluster.centerUtc - BLOCK_HALF_WIDTH_MS).coerceAtLeast(0f)
            val x1 = axis.x(cluster.centerUtc + BLOCK_HALF_WIDTH_MS).coerceAtMost(size.width)
            val alpha = min(cluster.total / fullIntensityAt, 1f).coerceAtLeast(MIN_ALPHA)
            drawRect(color.copy(alpha = alpha), Offset(x0, 0f), Size(x1 - x0, size.height))
        }
    }
}

private const val MIN_ALPHA = 0.15f

/** Full-intensity totals: a 100 g meal / a 10 U bolus draws a fully opaque block. */
const val CARBS_FULL_INTENSITY_G = 100f
const val INSULIN_FULL_INTENSITY_U = 10f
