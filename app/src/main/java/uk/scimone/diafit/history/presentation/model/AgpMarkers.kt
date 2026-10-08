package uk.scimone.diafit.history.presentation.model

import uk.scimone.diafit.core.domain.model.AgpProfile

/**
 * When carbs, boluses and activity usually happen: every event of the period folded onto one 24 h clock
 * ([AgpProfile.BINS] bins), weighted by amount (grams, units, minutes active), lightly smoothed and scaled so the
 * busiest bin of each is 1. Drawn as small soft area curves under the glucose profile.
 */
class AgpMarkers(
    val carbs: FloatArray,
    val bolus: FloatArray,
    val activity: FloatArray
) {
    companion object {
        val EMPTY = AgpMarkers(FloatArray(AgpProfile.BINS), FloatArray(AgpProfile.BINS), FloatArray(AgpProfile.BINS))
    }
}

private const val BINS = AgpProfile.BINS
private val KERNEL = floatArrayOf(1f, 4f, 6f, 4f, 1f)

fun List<DayHistoryUi>.toAgpMarkers(): AgpMarkers {
    val carbs = DoubleArray(BINS)
    val bolus = DoubleArray(BINS)
    val activity = DoubleArray(BINS)
    for (day in this) {
        val start = day.dayStartUtc
        val length = (day.dayEndUtc - start).toDouble()
        fun frac(t: Long) = ((t - start) / length).coerceIn(0.0, 1.0)
        fun addEvent(into: DoubleArray, t: Long, amount: Float) {
            into[(frac(t) * BINS).toInt().coerceAtMost(BINS - 1)] += amount.toDouble()
        }
        fun addSpan(from: Long, to: Long) {
            val a = frac(from) * BINS
            val b = frac(to) * BINS
            var bin = a.toInt()
            while (bin < b && bin < BINS) {
                activity[bin] += minOf(b, bin + 1.0) - maxOf(a, bin.toDouble())
                bin++
            }
        }
        day.carbs.forEach { c -> c.events.forEach { addEvent(carbs, it.timeUtc, it.value) } }
        day.insulin.forEach { c -> c.events.forEach { addEvent(bolus, it.timeUtc, it.value) } }
        day.elevatedActivity.forEach { addSpan(it.startUtc, it.endUtc) }
        day.activity.exercise.forEach { addSpan(it.startUtc, it.endUtc) }
    }
    return AgpMarkers(normalize(carbs), normalize(bolus), normalize(activity))
}

/** Circular smoothing with a small bell kernel, then scale to a 0..1 peak. */
private fun normalize(raw: DoubleArray): FloatArray {
    val smooth = FloatArray(BINS) { i ->
        var sum = 0f
        for (k in KERNEL.indices) sum += KERNEL[k] * raw[(i + k - 2 + BINS) % BINS].toFloat()
        sum / 16f
    }
    val max = smooth.max()
    if (max > 0f) for (i in smooth.indices) smooth[i] /= max
    return smooth
}
