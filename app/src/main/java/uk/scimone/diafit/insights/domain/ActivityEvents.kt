package uk.scimone.diafit.insights.domain

import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.ElevatedActivity
import uk.scimone.diafit.core.domain.model.HeartRateBucket

/** One stretch of physical activity: a logged workout, or elevated activity found in heart rate and steps. */
data class ActivityEvent(
    val startUtc: Long,
    val endUtc: Long,
    /** "Running", "Walking"..., or "Activity" for a detected stretch. */
    val label: String,
    val isWorkout: Boolean,
    /** Mean heart rate during the event, when there is heart-rate data for it. */
    val meanBpm: Double? = null
) {
    val durationMin: Int get() = ((endUtc - startUtc) / 60_000L).toInt()
}

object ActivityEvents {
    /** Workouts, plus detected stretches of activity that don't overlap a workout. Oldest first. */
    fun build(data: ActivityData): List<ActivityEvent> {
        val buckets = data.heartRate.groupBy { it.timestamp / ElevatedActivity.SLOT_MS }
            .map { (slot, rows) -> HeartRateBucket(slot * ElevatedActivity.SLOT_MS, rows.map { it.bpm }.average()) }
            .sortedBy { it.startUtc }
        fun meanBpm(from: Long, to: Long): Double? =
            buckets.filter { it.startUtc >= from - ElevatedActivity.SLOT_MS / 2 && it.startUtc < to }.map { it.bpm }.takeIf { it.isNotEmpty() }?.average()

        val workouts = data.exercise.map {
            ActivityEvent(it.startUtc, it.endUtc, it.title?.takeIf { t -> t.isNotBlank() } ?: "Workout", true, meanBpm(it.startUtc, it.endUtc))
        }
        val detected = ElevatedActivity.detect(buckets, data.steps, data.sleepSessions)
            .filter { span -> workouts.none { it.startUtc < span.endUtc && it.endUtc > span.startUtc } }
            .map { ActivityEvent(it.startUtc, it.endUtc, "Activity", false, meanBpm(it.startUtc, it.endUtc)) }
        return (workouts + detected).sortedBy { it.startUtc }
    }
}
