package uk.scimone.diafit.patterns.domain

import kotlin.math.abs

/*
 * AGP pattern detection, ported from the Diafit backend (`summary/features/agp/patterns.py`) as specified in
 * `patterns.md`. Thresholds, comparison strictness, iteration order and the output strings must stay exactly as
 * written there: the reference vectors in PatternDetectionTest come from the backend's own code.
 */

private const val HYPO_THRESHOLD = 70.0
private const val SEVERE_HYPO_THRESHOLD = 54.0
private const val TARGET_HIGH = 180.0
private const val VERY_HIGH = 250.0
private const val OPTIMAL_LOW = 70.0
private const val OPTIMAL_HIGH = 140.0
private const val FASTING_OPTIMAL_LOW = 70.0
private const val FASTING_OPTIMAL_HIGH = 100.0
private const val FASTING_TARGET_HIGH = 130.0
private const val WIDE_IQR = 60.0
private const val TIGHT_IQR = 30.0
private const val CONSISTENCY_THRESHOLD = 100.0
private const val CONSISTENT_OUTER_BAND = 40.0
private const val MEAL_SPIKE_THRESHOLD = 50.0
private const val DAWN_RISE_THRESHOLD = 20.0
private const val RAPID_SPIKE_TIME_THRESHOLD = 30
private const val RAPID_SPIKE_RISE_THRESHOLD = 50.0
private const val PROLONGED_ELEVATION_DURATION = 90
private const val ELEVATION_THRESHOLD_OFFSET = 30.0
private const val RECOVERY_THRESHOLD = 20.0
private const val RECOVERY_THRESHOLD_DELAYED = 30.0
private const val GOOD_MEAL_RISE_MIN = 20.0
private const val GOOD_MEAL_RISE_MAX = 40.0
private const val GOOD_MEAL_PEAK_TIME_MIN = 30
private const val GOOD_MEAL_PEAK_TIME_MAX = 90

private const val PER_HOUR = 12

/** Time-of-day periods, in detection order; `start > end` wraps midnight. */
internal enum class DayPeriod(val label: String, val startHour: Int, val endHour: Int) {
    NIGHT("night", 22, 7), MORNING("morning", 7, 11), NOON("noon", 11, 15),
    AFTERNOON("afternoon", 15, 18), EVENING("evening", 18, 22);

    val indices: IntArray = if (startHour > endHour) {
        ((startHour * PER_HOUR until PatternAgp.BINS) + (0 until endHour * PER_HOUR)).toIntArray()
    } else (startHour * PER_HOUR until endHour * PER_HOUR).toList().toIntArray()
}

private class MealWindow(val name: String, val pre: Int, val start: Int, val end: Int)

private val MEALS = listOf(MealWindow("breakfast", 6, 7, 10), MealWindow("lunch", 11, 12, 15), MealWindow("dinner", 18, 19, 22))

private fun DoubleArray.at(idx: IntArray) = DoubleArray(idx.size) { this[idx[it]] }
private fun DoubleArray.slice(from: Int, until: Int) = copyOfRange(from, until)
private fun DoubleArray.mean() = sum() / size

/** The pattern strings for [agp], in the backend's order. Empty = no patterns. */
fun detectAgpPatternTexts(agp: PatternAgp): List<String> {
    val p10 = agp.p10; val p25 = agp.p25; val p50 = agp.p50; val p75 = agp.p75; val p90 = agp.p90
    val iqr = DoubleArray(PatternAgp.BINS) { p75[it] - p25[it] }
    val outer = DoubleArray(PatternAgp.BINS) { p90[it] - p10[it] }
    val out = ArrayList<String>()

    // 1. Hypoglycemia
    for (p in DayPeriod.entries) {
        val idx = p.indices
        when {
            p50.at(idx).min() < HYPO_THRESHOLD -> out += "Consistent hypoglycemia during ${p.label}"
            p10.at(idx).min() < SEVERE_HYPO_THRESHOLD -> out += "Sporadic, very dangerous hypoglycemia during ${p.label}"
            p25.at(idx).min() < HYPO_THRESHOLD -> out += "Recurring hypoglycemia during ${p.label}"
        }
    }
    // 2. Hyperglycemia
    for (p in DayPeriod.entries) {
        val idx = p.indices
        when {
            p50.at(idx).mean() > VERY_HIGH -> out += "Very high glucose during ${p.label}"
            p50.at(idx).mean() > TARGET_HIGH -> out += "Elevated glucose during ${p.label}"
            p75.at(idx).mean() > TARGET_HIGH -> out += "Frequent glucose elevations during ${p.label}"
        }
    }
    // 3. Meal spikes
    for (m in MEALS) {
        val pre = p50.slice(m.pre * PER_HOUR, m.start * PER_HOUR).mean()
        val post = p50.slice(m.start * PER_HOUR, m.end * PER_HOUR).max()
        if (post - pre > MEAL_SPIKE_THRESHOLD) out += "Post-${m.name} glucose spike"
    }
    // 4. Dawn phenomenon
    if (p50.slice(78, 84).mean() - p50.slice(36, 42).mean() > DAWN_RISE_THRESHOLD) out += "Dawn phenomenon detected"
    // 5. Somogyi effect
    if (p50.slice(24, 48).min() < HYPO_THRESHOLD && p50.slice(72, 96).mean() > TARGET_HIGH) {
        out += "Possible rebound hyperglycemia (Somogyi effect)"
    }
    // 6. Fasting
    run {
        val m = p50.slice(60, 84).mean()
        val q = iqr.slice(60, 84).mean()
        when {
            m > FASTING_TARGET_HIGH -> out += "Elevated fasting glucose levels"
            m >= FASTING_OPTIMAL_LOW && m <= FASTING_OPTIMAL_HIGH && q < TIGHT_IQR -> out += "Optimal fasting glucose control"
            m < FASTING_OPTIMAL_LOW -> out += "Low fasting glucose levels"
        }
    }
    // 7. Meal response quality
    for (m in MEALS) {
        val start = m.start * PER_HOUR
        val end = m.end * PER_HOUR
        val baseline = p50.slice(m.pre * PER_HOUR, start).mean()
        val window = p50.slice(start, end)
        val peak = window.max()
        val ttpMin = window.indexOfFirst { it == peak } * 5
        val rise = peak - baseline
        val elevMin = window.count { it > baseline + ELEVATION_THRESHOLD_OFFSET } * 5
        val finalG = p50.slice(end - 6, end).mean()
        val recovered = abs(finalG - baseline) < RECOVERY_THRESHOLD
        if (ttpMin < RAPID_SPIKE_TIME_THRESHOLD && rise > RAPID_SPIKE_RISE_THRESHOLD) out += "Rapid post-${m.name} glucose spike"
        if (elevMin > PROLONGED_ELEVATION_DURATION) out += "Extended post-${m.name} elevation"
        if (!recovered && finalG > baseline + RECOVERY_THRESHOLD_DELAYED) out += "Slow post-${m.name} glucose recovery"
        if (rise > GOOD_MEAL_RISE_MIN && rise < GOOD_MEAL_RISE_MAX &&
            ttpMin > GOOD_MEAL_PEAK_TIME_MIN && ttpMin < GOOD_MEAL_PEAK_TIME_MAX && recovered
        ) out += "Well-controlled post-${m.name} glucose response"
    }
    // 8. Variability
    for (p in DayPeriod.entries) if (iqr.at(p.indices).mean() > WIDE_IQR) out += "High glucose variability during ${p.label}"
    // 9. Tight control
    for (p in DayPeriod.entries) {
        val m = p50.at(p.indices).mean()
        if (m >= OPTIMAL_LOW && m <= OPTIMAL_HIGH && iqr.at(p.indices).mean() < TIGHT_IQR) out += "Tight glucose control during ${p.label}"
    }
    // 10. Overall
    run {
        val m = p50.mean()
        val q = iqr.mean()
        when {
            m >= OPTIMAL_LOW && m <= OPTIMAL_HIGH && q < TIGHT_IQR -> out += "Excellent overall glucose control"
            m < OPTIMAL_LOW -> out += "Overall glucose trending low"
            m > TARGET_HIGH -> out += "Overall glucose trending high"
        }
    }
    // 11. Consistency
    for (p in DayPeriod.entries) {
        val band = outer.at(p.indices).mean()
        when {
            band > CONSISTENCY_THRESHOLD -> out += "Inconsistent glucose patterns during ${p.label}"
            band < CONSISTENT_OUTER_BAND -> out += "Consistent glucose patterns during ${p.label}"
        }
    }
    return out
}

/** How a pattern reads to the user: something to fix urgently, something to look at, or something going well. */
enum class PatternTone { DANGER, CONCERN, GOOD }

/** Hours of the day to highlight on the AGP; `startHour > endHour` wraps midnight. */
data class HourSpan(val startHour: Int, val endHour: Int) {
    val label: String get() = "%02d:00–%02d:00".format(startHour, endHour)
}

/** One detected pattern with what the UI needs: tone and where on the AGP it lives. */
data class AgpPattern(val text: String) {
    val tone: PatternTone = toneOf(text)
    val highlight: HourSpan? = highlightOf(text)

    /** Worth a notification: patterns to act on, not reassurance. */
    val isAlert: Boolean get() = tone != PatternTone.GOOD
}

fun detectAgpPatterns(agp: PatternAgp): List<AgpPattern> = detectAgpPatternTexts(agp).map(::AgpPattern)

private fun toneOf(text: String): PatternTone {
    val t = text.lowercase()
    return when {
        "hypoglycemia" in t || "very high" in t || "trending low" in t || "low fasting" in t || "somogyi" in t -> PatternTone.DANGER
        t.startsWith("optimal") || t.startsWith("well-controlled") || t.startsWith("tight") ||
            t.startsWith("excellent") || t.startsWith("consistent glucose") -> PatternTone.GOOD
        else -> PatternTone.CONCERN
    }
}

/**
 * The web dashboard's highlight rule (`patterns.md` §4): first key, in this order, contained in the lower-cased
 * text ("afternoon" before "noon"). One addition: the Somogyi pattern highlights the 02:00–08:00 stretch its
 * detector looks at, which the dashboard leaves unhighlighted.
 */
private val HIGHLIGHT_KEYS = listOf(
    "night" to HourSpan(22, 7), "morning" to HourSpan(7, 11), "afternoon" to HourSpan(15, 18),
    "noon" to HourSpan(11, 15), "lunch" to HourSpan(11, 15), "evening" to HourSpan(18, 22),
    "dinner" to HourSpan(18, 22), "breakfast" to HourSpan(7, 10), "dawn" to HourSpan(3, 7),
    "fasting" to HourSpan(5, 7), "overnight" to HourSpan(22, 7), "somogyi" to HourSpan(2, 8)
)

internal fun highlightOf(text: String): HourSpan? {
    val t = text.lowercase()
    return HIGHLIGHT_KEYS.firstOrNull { (key, _) -> key in t }?.second
}
