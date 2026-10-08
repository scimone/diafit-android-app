package uk.scimone.diafit.history.presentation.model

import uk.scimone.diafit.core.domain.model.ActivitySpan
import uk.scimone.diafit.history.domain.model.TreatmentCluster
import uk.scimone.diafit.history.domain.model.TreatmentEvent
import kotlin.math.sqrt

/** Length of the reference day every day is folded onto. */
const val REFERENCE_DAY_MS = 24 * 60 * 60_000L

/**
 * Carb sittings, bolus sittings and activity ranges of all shown days moved onto one reference day (0..24 h from 0),
 * so the same strips as the per-day tracks can draw them on top of each other.
 */
class AgpMarkers(
    val carbs: List<TreatmentCluster>,
    val bolus: List<TreatmentCluster>,
    val activity: List<ActivitySpan>,
    val dayCount: Int
) {
    /** Opacity factor per mark: the more days overlap, the fainter each one, so long periods don't turn solid. */
    val alphaScale: Float get() = 0.5f * sqrt(14f / maxOf(dayCount, 14))

    companion object {
        val EMPTY = AgpMarkers(emptyList(), emptyList(), emptyList(), 0)
    }
}

fun List<DayHistoryUi>.toAgpMarkers(): AgpMarkers {
    val carbs = ArrayList<TreatmentCluster>()
    val bolus = ArrayList<TreatmentCluster>()
    val activity = ArrayList<ActivitySpan>()
    for (day in this) {
        val shift = day.dayStartUtc
        fun TreatmentCluster.fold() = TreatmentCluster(events.map { TreatmentEvent(it.timeUtc - shift, it.value) })
        day.carbs.mapTo(carbs) { it.fold() }
        day.insulin.mapTo(bolus) { it.fold() }
        day.elevatedActivity.mapTo(activity) { ActivitySpan(it.startUtc - shift, it.endUtc - shift, it.intensity) }
        day.activity.exercise.mapTo(activity) { ActivitySpan(it.startUtc - shift, it.endUtc - shift, 1f) }
    }
    return AgpMarkers(carbs, bolus, activity, size)
}
