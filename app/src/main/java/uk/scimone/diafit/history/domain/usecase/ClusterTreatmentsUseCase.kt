package uk.scimone.diafit.history.domain.usecase

import uk.scimone.diafit.history.domain.model.TreatmentCluster
import uk.scimone.diafit.history.domain.model.TreatmentEvent

/**
 * Groups treatments into sittings: an entry joins the running cluster when it is within
 * [maxGapMs] of the previous one. (A 1-D single-linkage cut; for well separated meals it gives
 * the same groups as Ward clustering at a similar distance.)
 */
class ClusterTreatmentsUseCase {
    operator fun invoke(events: List<TreatmentEvent>, maxGapMs: Long = MAX_GAP_MS): List<TreatmentCluster> {
        val clusters = mutableListOf<MutableList<TreatmentEvent>>()
        events.sortedBy { it.timeUtc }.forEach { event ->
            val current = clusters.lastOrNull()
            if (current != null && event.timeUtc - current.last().timeUtc <= maxGapMs) current += event
            else clusters += mutableListOf(event)
        }
        return clusters.map(::TreatmentCluster)
    }

    companion object {
        const val MAX_GAP_MS = 90 * 60_000L
    }
}
