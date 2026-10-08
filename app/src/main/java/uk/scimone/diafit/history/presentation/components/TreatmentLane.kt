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
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import kotlin.math.min

/** Full-size totals: a 100 g meal / a 10 U bolus draws the biggest dot / tallest tick. */
const val CARBS_FULL_INTENSITY_G = 100f
const val INSULIN_FULL_INTENSITY_U = 10f

val TREATMENT_LANE_HEIGHT = 18.dp
private val MERGE_GAP = 8.dp
private const val CARB_MIN_RADIUS_DP = 2.2f
private const val CARB_MAX_RADIUS_DP = 4.5f
private const val TICK_MIN_DP = 2f
private const val TICK_MAX_DP = 6f

/** Marks closer than the minimum gap summed into one. */
internal class MergedMark(val x: Float, val total: Float)

/** Sorts (x, value) marks and merges chains of marks that are less than [minGap] apart into one at their value-weighted centre. */
internal fun List<Pair<Float, Float>>.mergeNearby(minGap: Float): List<MergedMark> {
    val groups = mutableListOf<MutableList<Pair<Float, Float>>>()
    sortedBy { it.first }.forEach { mark ->
        val current = groups.lastOrNull()
        if (current != null && mark.first - current.last().first < minGap) current += mark else groups += mutableListOf(mark)
    }
    return groups.map { g ->
        val total = g.sumOf { it.second.toDouble() }.toFloat()
        val x = if (total > 0f) g.sumOf { (it.first * it.second).toDouble() }.toFloat() / total else g.first().first
        MergedMark(x, total)
    }
}

/**
 * Carbs and insulin of one day on a shared hairline: carbs as dots resting on it (bigger = more grams), insulin as ticks
 * hanging from it (taller = more units). Entries that would overlap are summed into one mark when [mergeClose]; the
 * profile card draws every day's sittings on one lane instead, where [alphaScale] lets overlaps build up.
 */
@Composable
fun TreatmentLane(
    carbs: List<TreatmentCluster>,
    insulin: List<TreatmentCluster>,
    dayStartUtc: Long,
    dayEndUtc: Long,
    modifier: Modifier = Modifier,
    alphaScale: Float = 1f,
    mergeClose: Boolean = true
) {
    val guides = hourGuideColor()
    val hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    Canvas(modifier.fillMaxWidth().height(TREATMENT_LANE_HEIGHT)) {
        drawHourGuides(guides)
        val mid = size.height / 2f
        drawLine(hairline, Offset(0f, mid), Offset(size.width, mid), 1f)
        val axis = DayXAxis(dayStartUtc, dayEndUtc, size.width)
        val gap = if (mergeClose) MERGE_GAP.toPx() else 0f
        val alpha = (0.85f * alphaScale).coerceIn(0.15f, 1f)
        carbs.map { axis.x(it.centerUtc) to it.total }.mergeNearby(gap).forEach { m ->
            val radius = (CARB_MIN_RADIUS_DP + (CARB_MAX_RADIUS_DP - CARB_MIN_RADIUS_DP) * min(m.total / CARBS_FULL_INTENSITY_G, 1f)).dp.toPx()
            drawCircle(Carbs.copy(alpha = alpha), radius, Offset(m.x, mid - 3.dp.toPx()))
        }
        val tickWidth = 2.4.dp.toPx()
        insulin.map { axis.x(it.centerUtc) to it.total }.mergeNearby(gap).forEach { m ->
            val height = (TICK_MIN_DP + (TICK_MAX_DP - TICK_MIN_DP) * min(m.total / INSULIN_FULL_INTENSITY_U, 1f)).dp.toPx()
            drawRoundRect(Bolus.copy(alpha = alpha), Offset(m.x - tickWidth / 2, mid + 1.5.dp.toPx()), Size(tickWidth, height), CornerRadius(tickWidth / 2))
        }
    }
}
