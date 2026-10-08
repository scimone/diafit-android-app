package uk.scimone.diafit.core.domain.model

import kotlin.math.max

private const val MINUTE_MS = 60_000L

/** Sums [rate] per minute over [fromMs]..[toMs]. */
private inline fun areaPerMinute(fromMs: Long, toMs: Long, rate: (Long) -> Double): Double {
    var sum = 0.0
    var t = fromMs
    while (t < toMs) { sum += rate(t); t += MINUTE_MS }
    return sum
}

/**
 * Insulin still to act at [now]: the area under the remaining part of each bolus' activity curve, as a share
 * of the whole curve times the dose (i.e. what pumps call insulin on board). [bolus] pairs time with units.
 */
fun remainingInsulin(bolus: List<Pair<Long, Double>>, now: Long, diaHours: Double = 4.5): Double {
    val endAfter = (diaHours * 60 * MINUTE_MS).toLong()
    return bolus.sumOf { (time, units) ->
        if (units <= 0.0 || now >= time + endAfter) return@sumOf 0.0
        val activity = { t: Long -> InsulinActivity.calculate(units, time, t, dia = diaHours).activity }
        val total = areaPerMinute(time, time + endAfter, activity)
        if (total <= 0.0) 0.0 else units * areaPerMinute(max(now, time), time + endAfter, activity) / total
    }
}

/** Carbs still to be absorbed at [now] (carbs on board), by the same principle; [meals] are (time, grams, absorption minutes). */
fun remainingCarbs(meals: List<Triple<Long, Double, Int>>, now: Long): Double =
    meals.sumOf { (time, grams, minutes) ->
        val end = time + minutes * MINUTE_MS
        if (grams <= 0.0 || minutes <= 0 || now >= end) return@sumOf 0.0
        val activity = { t: Long -> CarbActivity.calculate(grams, time, t, minutes) }
        val total = areaPerMinute(time, end, activity)
        if (total <= 0.0) 0.0 else grams * areaPerMinute(max(now, time), end, activity) / total
    }

/** How active the person is right now, for the Home activity heading. */
enum class ActivityLevel(val label: String) {
    SLEEPING("Sleeping"), WORKOUT("Workout"), RESTING("Resting"), LIGHT("Light"), MODERATE("Moderate"), VIGOROUS("Vigorous")
}

private const val RECENT_HEART_MS = 15 * MINUTE_MS
private const val RECENT_STEPS_MS = 20 * MINUTE_MS

/**
 * The current [ActivityLevel]: asleep or in a workout if one covers [now]; otherwise the higher of the level the
 * latest heart rate (against the person's resting rate: 20th percentile of the data, clamped 45..75) and the
 * latest 15-minute step count suggest. Null when there is no recent heart rate or steps.
 */
fun currentActivityLevel(data: ActivityData, now: Long): ActivityLevel? {
    if (data.sleepSessions.any { now >= it.startUtc && now < it.endUtc }) return ActivityLevel.SLEEPING
    if (data.exercise.any { now >= it.startUtc && now <= it.endUtc }) return ActivityLevel.WORKOUT

    val heart = data.heartRate.filter { it.timestamp <= now }.maxByOrNull { it.timestamp }?.takeIf { now - it.timestamp <= RECENT_HEART_MS }
    val steps = data.steps.filter { it.startUtc <= now }.maxByOrNull { it.startUtc }
        ?.takeIf { now - (it.startUtc + StepsEntity.STEP_BUCKET_MS) <= RECENT_STEPS_MS }
    if (heart == null && steps == null) return null

    val fromHeart = heart?.let {
        val sorted = data.heartRate.map { h -> h.bpm }.sorted()
        val resting = sorted.getOrNull((sorted.size * 0.2).toInt())?.coerceIn(45, 75) ?: 60
        when (it.bpm - resting) { in Int.MIN_VALUE until 15 -> 0; in 15 until 30 -> 1; in 30 until 50 -> 2; else -> 3 }
    } ?: 0
    val fromSteps = steps?.let { when { it.count < 150 -> 0; it.count < 450 -> 1; it.count < 900 -> 2; else -> 3 } } ?: 0
    return when (max(fromHeart, fromSteps)) {
        0 -> ActivityLevel.RESTING; 1 -> ActivityLevel.LIGHT; 2 -> ActivityLevel.MODERATE; else -> ActivityLevel.VIGOROUS
    }
}
