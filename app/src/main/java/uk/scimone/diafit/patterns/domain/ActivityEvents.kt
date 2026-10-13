package uk.scimone.diafit.patterns.domain

import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.ElevatedActivity
import uk.scimone.diafit.core.domain.model.HeartRateBucket

/** One stretch of physical activity: a logged workout, or elevated activity found in heart rate and steps. */
data class ActivityEvent(
    val startUtc: Long,
    val endUtc: Long,
    /** "Running", "Walking"..., or [LABEL_ACTIVITY] for a detected stretch ([LABEL_WORKOUT] for an untitled workout). */
    val label: String,
    val isWorkout: Boolean,
    /** Mean heart rate during the event, when there is heart-rate data for it. */
    val meanBpm: Double? = null
) {
    val durationMin: Int get() = ((endUtc - startUtc) / 60_000L).toInt()
}

object ActivityEvents {
    /** Generic labels are string-resource references ([ref]), so they show in the language of the day. */
    val LABEL_WORKOUT = ref("pat_ev_workout")
    val LABEL_ACTIVITY = ref("pat_ev_activity")

    /** Workouts, plus detected stretches of activity that don't overlap a workout. Oldest first. */
    fun build(data: ActivityData): List<ActivityEvent> {
        val buckets = data.heartRate.groupBy { it.timestamp / ElevatedActivity.SLOT_MS }
            .map { (slot, rows) -> HeartRateBucket(slot * ElevatedActivity.SLOT_MS, rows.map { it.bpm }.average()) }
            .sortedBy { it.startUtc }
        fun meanBpm(from: Long, to: Long): Double? =
            buckets.filter { it.startUtc >= from - ElevatedActivity.SLOT_MS / 2 && it.startUtc < to }.map { it.bpm }.takeIf { it.isNotEmpty() }?.average()

        val workouts = data.exercise.map {
            ActivityEvent(it.startUtc, it.endUtc, it.title?.takeIf { t -> t.isNotBlank() } ?: LABEL_WORKOUT, true, meanBpm(it.startUtc, it.endUtc))
        }
        val detected = ElevatedActivity.detect(buckets, data.steps, data.sleepSessions)
            .filter { span -> workouts.none { it.startUtc < span.endUtc && it.endUtc > span.startUtc } }
            .map { ActivityEvent(it.startUtc, it.endUtc, LABEL_ACTIVITY, false, meanBpm(it.startUtc, it.endUtc)) }
        return (workouts + detected).sortedBy { it.startUtc }
    }
}
