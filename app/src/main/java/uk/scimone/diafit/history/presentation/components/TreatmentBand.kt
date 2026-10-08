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
import uk.scimone.diafit.history.domain.model.TreatmentCluster
import kotlin.math.min

/** Full-strength totals: a 100 g meal / a 10 U bolus draws a fully opaque block. */
const val CARBS_FULL_INTENSITY_G = 100f
const val INSULIN_FULL_INTENSITY_U = 10f

val TREATMENT_BAND_HEIGHT = 7.dp
private const val BAND_HALF_WIDTH_MS = 30 * 60_000L
private const val BAND_MIN_ALPHA = 0.2f

/**
 * One kind of treatment as a band of rounded blocks, drawn like the [ActivityLane]: every sitting is a one-hour block centred
 * on it, stronger the more it holds (up to [fullIntensityAt]). The day tiles and the profile card stack one band per kind
 (the profile card with every day folded onto one, so [alphaScale] lets overlapping blocks build up).
 */
@Composable
fun TreatmentBand(
    clusters: List<TreatmentCluster>,
    dayStartUtc: Long,
    dayEndUtc: Long,
    color: androidx.compose.ui.graphics.Color,
    fullIntensityAt: Float,
    modifier: Modifier = Modifier,
    alphaScale: Float = 1f
) {
    val guides = hourGuideColor()
    val floor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Canvas(modifier.fillMaxWidth().height(TREATMENT_BAND_HEIGHT)) {
        drawHourGuides(guides)
        drawLine(floor, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
        val axis = DayXAxis(dayStartUtc, dayEndUtc, size.width)
        val corner = CornerRadius(minOf(size.height / 2, 3.dp.toPx()))
        clusters.forEach { cluster ->
            val x0 = axis.x(cluster.centerUtc - BAND_HALF_WIDTH_MS).coerceAtLeast(0f)
            val x1 = axis.x(cluster.centerUtc + BAND_HALF_WIDTH_MS).coerceAtMost(size.width)
            if (x1 <= x0) return@forEach
            val alpha = min(cluster.total / fullIntensityAt, 1f).coerceAtLeast(BAND_MIN_ALPHA) * alphaScale
            drawRoundRect(color.copy(alpha = alpha.coerceIn(0f, 1f)), Offset(x0, 0f), Size(x1 - x0, size.height), corner)
        }
    }
}
