package uk.scimone.diafit.history.presentation.model

import uk.scimone.diafit.history.presentation.components.CARBS_FULL_INTENSITY_G
import uk.scimone.diafit.history.presentation.components.INSULIN_FULL_INTENSITY_U

/** A mark on the 24 h clock: [start]..[end] as fractions of the day, [weight] 0..1 (amount or intensity). */
data class ClockMarker(val start: Float, val end: Float, val weight: Float)

/**
 * Every carb, bolus and activity event of the period folded onto one 24 h clock, so drawing them
 * semi-transparent on top of each other shows when most events happen.
 */
class AgpMarkers(
    val carbs: List<ClockMarker>,
    val bolus: List<ClockMarker>,
    val activity: List<ClockMarker>,
    val dayCount: Int
)

private const val EVENT_HALF_WIDTH = 6f / (24 * 60)

fun List<DayHistoryUi>.toAgpMarkers(): AgpMarkers {
    val carbs = ArrayList<ClockMarker>()
    val bolus = ArrayList<ClockMarker>()
    val activity = ArrayList<ClockMarker>()
    for (day in this) {
        val start = day.dayStartUtc
        val length = (day.dayEndUtc - start).toFloat()
        fun frac(t: Long) = ((t - start) / length).coerceIn(0f, 1f)
        fun event(list: MutableList<ClockMarker>, t: Long, weight: Float) {
            val f = frac(t)
            list += ClockMarker((f - EVENT_HALF_WIDTH).coerceAtLeast(0f), (f + EVENT_HALF_WIDTH).coerceAtMost(1f), weight.coerceIn(0f, 1f))
        }
        day.carbs.forEach { c -> c.events.forEach { event(carbs, it.timeUtc, it.value / CARBS_FULL_INTENSITY_G) } }
        day.insulin.forEach { c -> c.events.forEach { event(bolus, it.timeUtc, it.value / INSULIN_FULL_INTENSITY_U) } }
        day.elevatedActivity.forEach {
            val a = frac(it.startUtc)
            val b = frac(it.endUtc)
            if (b > a) activity += ClockMarker(a, b, 0.3f * it.intensity)
        }
        day.activity.exercise.forEach {
            val a = frac(it.startUtc)
            val b = frac(it.endUtc)
            if (b > a) activity += ClockMarker(a, b, 0.5f)
        }
    }
    return AgpMarkers(carbs, bolus, activity, size)
}
