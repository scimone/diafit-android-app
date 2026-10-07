package uk.scimone.diafit.core.domain.model

import kotlin.math.max

/** Heart rate averaged over one [ElevatedActivity.SLOT_MS] slot. */
data class HeartRateBucket(val startUtc: Long, val bpm: Double)

/** A stretch of raised activity found in the heart-rate and step data (not a logged workout). */
data class ActivitySpan(val startUtc: Long, val endUtc: Long, /** 0..1, how far above the threshold it got. */ val intensity: Float)

/**
 * Finds time ranges of elevated activity from heart rate and steps, for the History overview where
 * most days have no logged workout.
 *
 * The data is cut into 5-minute slots. A slot is **active** when
 *  - the steps of its 15-minute bucket reach [STEPS_ACTIVE_PER_15_MIN] (about 30 steps/min, a stroll), or
 *  - its mean heart rate reaches `max(resting + 30, 95)` bpm, where *resting* is the 20th percentile of
 *    all slots in the range (clamped to 45..75), so the threshold follows the person.
 * Slots during sleep never count. Active slots are merged across gaps of up to 10 minutes, and ranges
 * shorter than 10 minutes (a flight of stairs, a coffee spike) are dropped.
 */
object ElevatedActivity {
    const val SLOT_MS = 5 * 60_000L
    const val STEPS_ACTIVE_PER_15_MIN = 450
    private const val STEPS_FULL_PER_15_MIN = 1500f
    private const val HR_ABOVE_RESTING = 30
    private const val HR_MIN_THRESHOLD = 95
    private const val HR_FULL = 150
    private const val MAX_GAP_SLOTS = 2
    private const val MIN_SLOTS = 2

    fun detect(heartRate: List<HeartRateBucket>, steps: List<StepsEntity>, sleep: List<SleepSession>): List<ActivitySpan> {
        val resting = restingBpm(heartRate)
        val hrThreshold = max(resting + HR_ABOVE_RESTING, HR_MIN_THRESHOLD).toFloat()

        // Intensity per slot start; only active slots are present.
        val slots = sortedMapOf<Long, Float>()
        heartRate.forEach {
            if (it.bpm >= hrThreshold) {
                val level = ((it.bpm.toFloat() - hrThreshold) / (HR_FULL - hrThreshold)).coerceIn(0f, 1f)
                slots.merge(it.startUtc, level, ::maxOf)
            }
        }
        steps.forEach { st ->
            if (st.count >= STEPS_ACTIVE_PER_15_MIN) {
                val level = (st.count / STEPS_FULL_PER_15_MIN).coerceIn(0f, 1f)
                var t = st.startUtc
                while (t < st.startUtc + StepsEntity.STEP_BUCKET_MS) { slots.merge(t, level, ::maxOf); t += SLOT_MS }
            }
        }
        val asleep = sleep.map { it.startUtc to it.endUtc }
        asleep.forEach { (s, e) -> slots.keys.removeAll { it + SLOT_MS > s && it < e } }
        if (slots.isEmpty()) return emptyList()

        val spans = mutableListOf<ActivitySpan>()
        var runStart = -1L; var runEnd = -1L; var levelSum = 0f; var count = 0
        fun flush() {
            if (count >= MIN_SLOTS) spans += ActivitySpan(runStart, runEnd, (levelSum / count).coerceIn(MIN_INTENSITY, 1f))
        }
        slots.forEach { (start, level) ->
            if (count > 0 && start - runEnd <= MAX_GAP_SLOTS * SLOT_MS) {
                runEnd = start + SLOT_MS; levelSum += level; count++
            } else {
                if (count > 0) flush()
                runStart = start; runEnd = start + SLOT_MS; levelSum = level; count = 1
            }
        }
        flush()
        return spans
    }

    /** Resting estimate: 20th percentile of the slot means, clamped so odd data can't make the threshold absurd. */
    fun restingBpm(heartRate: List<HeartRateBucket>): Int {
        if (heartRate.isEmpty()) return 60
        val sorted = heartRate.map { it.bpm }.sorted()
        return sorted[(sorted.size * 0.2).toInt().coerceAtMost(sorted.size - 1)].toInt().coerceIn(45, 75)
    }

    private const val MIN_INTENSITY = 0.3f
}
