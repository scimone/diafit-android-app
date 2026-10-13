package uk.scimone.diafit.patterns.domain

import kotlin.math.roundToInt

/** One event's glucose around its start: 5-minute values from [firstK] and its starting level. */
class EventCurve(val values: FloatArray, val baseline: Float)

/**
 * The event-aligned graph shared by the meal and exercise detectors: for every [stepBins] x 5 minutes, the median and
 * middle half of the change from each event's own starting level. A point is only trusted when at least half of the
 * events (and 3) have a reading there; otherwise the previous point is carried on.
 */
internal fun eventGraph(
    group: List<EventCurve>, compare: List<EventCurve>?, firstK: Int, lastK: Int, stepBins: Int,
    aLabel: Msg, bLabel: Msg?, eventLabel: Msg, spanMinutes: Int?, axis: List<Int>
): MiniGraph.EventResponse {
    fun series(curves: List<EventCurve>, fraction: Double): List<Int> {
        var last = 0
        return (firstK..lastK step stepBins).map { k ->
            val vs = curves.mapNotNull { c ->
                (k until k + stepBins).map { c.values[it.coerceAtMost(lastK) - firstK] - c.baseline }.filter { !it.isNaN() }
                    .takeIf { it.isNotEmpty() }?.average()
            }.sorted()
            if (vs.size >= maxOf(3, curves.size / 2)) last = vs[((vs.size - 1) * fraction).roundToInt()].roundToInt()
            last
        }
    }
    return MiniGraph.EventResponse(
        startMinute = firstK * BIN_MINUTES, stepMinutes = stepBins * BIN_MINUTES,
        median = series(group, 0.5), p25 = series(group, 0.25), p75 = series(group, 0.75), aLabel = aLabel,
        compare = compare?.let { series(it, 0.5) }, bLabel = bLabel, eventLabel = eventLabel, spanMinutes = spanMinutes, axis = axis
    )
}

/** The AGP of [days] as pattern-detection input, or null when there are too few days or sparse times of day. */
internal fun agpOfDays(days: List<DayGlucose>): uk.scimone.diafit.patterns.domain.PatternAgp? {
    val minutes = ArrayList<Int>(); val values = ArrayList<Int>()
    for (d in days) for (i in d.bins.indices) if (!d.bins[i].isNaN()) { minutes += i * BIN_MINUTES; values += d.bins[i].roundToInt() }
    return uk.scimone.diafit.patterns.domain.PatternAgp.build(minutes.toIntArray(), values.toIntArray(), days.size)
}
