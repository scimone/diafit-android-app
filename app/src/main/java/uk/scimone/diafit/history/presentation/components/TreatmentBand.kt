package uk.scimone.diafit.history.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import uk.scimone.diafit.history.domain.model.TreatmentCluster
import uk.scimone.diafit.history.domain.model.TreatmentEvent
import kotlin.math.min

/** Full-strength totals: a 100 g meal / a 10 U bolus draws a fully opaque block. */
const val CARBS_FULL_INTENSITY_G = 100f
const val INSULIN_FULL_INTENSITY_U = 10f

val TREATMENT_BAND_HEIGHT = 5.dp
private const val BLOCK_PAD_MS = 6 * 60_000L
private val BLOCK_MIN_WIDTH = 4.dp
private const val BAND_MIN_ALPHA = 0.2f

/** Entries closer than this (carbs, or insulin doses) share one block on the day tiles. */
const val BAND_GROUP_GAP_MS = 20 * 60_000L

/** A stretch of entries drawn as one block: first to last entry and everything they add up to. */
internal class EventBlock(val startUtc: Long, val endUtc: Long, val total: Float)

/** Sorts [this] and chains entries that are at most [maxGapMs] apart into one block each. */
internal fun List<TreatmentEvent>.groupForBand(maxGapMs: Long): List<EventBlock> {
    val groups = mutableListOf<MutableList<TreatmentEvent>>()
    sortedBy { it.timeUtc }.forEach { e ->
        val current = groups.lastOrNull()
        if (current != null && e.timeUtc - current.last().timeUtc <= maxGapMs) current += e else groups += mutableListOf(e)
    }
    return groups.map { g -> EventBlock(g.first().timeUtc, g.last().timeUtc, g.sumOf { it.value.toDouble() }.toFloat()) }
}

/**
 * One kind of treatment as a band of small rounded blocks, drawn like the [ActivityLane]: each entry is a narrow block,
 * stronger the more it holds (up to [fullIntensityAt]). Only entries within [groupGapMs] of each other (a lot in a short
 * time) are joined into one wider block. The day tiles and the profile card stack one band per kind; the profile card folds
 * every day onto one (no grouping, [alphaScale] lets overlapping blocks build up).
 */
@Composable
fun TreatmentBand(
    clusters: List<TreatmentCluster>,
    dayStartUtc: Long,
    dayEndUtc: Long,
    color: androidx.compose.ui.graphics.Color,
    fullIntensityAt: Float,
    modifier: Modifier = Modifier,
    alphaScale: Float = 1f,
    groupGapMs: Long = BAND_GROUP_GAP_MS
) {
    val guides = hourGuideColor()
    val floor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val blocks = remember(clusters, groupGapMs) { clusters.flatMap { it.events }.groupForBand(groupGapMs) }
    Canvas(modifier.fillMaxWidth().height(TREATMENT_BAND_HEIGHT)) {
        drawHourGuides(guides)
        drawLine(floor, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
        val axis = DayXAxis(dayStartUtc, dayEndUtc, size.width)
        val corner = CornerRadius(minOf(size.height / 2, 3.dp.toPx()))
        val minWidth = BLOCK_MIN_WIDTH.toPx()
        blocks.forEach { block ->
            var x0 = axis.x(block.startUtc - BLOCK_PAD_MS)
            var x1 = axis.x(block.endUtc + BLOCK_PAD_MS)
            if (x1 - x0 < minWidth) { val c = (x0 + x1) / 2; x0 = c - minWidth / 2; x1 = c + minWidth / 2 }
            x0 = x0.coerceAtLeast(0f); x1 = x1.coerceAtMost(size.width)
            if (x1 <= x0) return@forEach
            val alpha = min(block.total / fullIntensityAt, 1f).coerceAtLeast(BAND_MIN_ALPHA) * alphaScale
            drawRoundRect(color.copy(alpha = alpha.coerceIn(0f, 1f)), Offset(x0, 0f), Size(x1 - x0, size.height), corner)
        }
    }
}
