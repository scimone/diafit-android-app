package uk.scimone.diafit.core.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One heart-rate value per minute (the mean of every sample of that minute, whatever device wrote it). */
@Entity(indices = [Index(value = ["timestamp"], unique = true)])
data class HeartRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    /** Start of the minute, epoch ms. */
    val timestamp: Long,
    val bpm: Int
)

/** Steps taken in one [STEP_BUCKET_MS] slot (de-duplicated across devices by Health Connect's aggregation). */
@Entity(indices = [Index(value = ["startUtc"], unique = true)])
data class StepsEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val startUtc: Long,
    val count: Int
) {
    companion object {
        const val STEP_BUCKET_MS = 15 * 60_000L
    }
}

/** One stage of a sleep session; a session without stage data is stored as one [SleepStage.SLEEPING] row. */
@Entity(indices = [Index(value = ["sessionId", "startUtc"], unique = true), Index(value = ["endUtc"])])
data class SleepStageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    /** Health Connect record id of the session, so its stages stay together and are replaced together. */
    val sessionId: String,
    val startUtc: Long,
    val endUtc: Long,
    /** [SleepStage.code] */
    val stage: Int,
    /** Start/end of the whole session (denormalised so a stage alone can be drawn and summed). */
    val sessionStartUtc: Long,
    val sessionEndUtc: Long
) {
    val sleepStage: SleepStage get() = SleepStage.fromCode(stage)
}

/** A workout / exercise session. */
@Entity(indices = [Index(value = ["sourceId"], unique = true), Index(value = ["endUtc"])])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val sourceId: String,
    val startUtc: Long,
    val endUtc: Long,
    /** Health Connect `ExerciseSessionRecord.EXERCISE_TYPE_*`. */
    val exerciseType: Int,
    val title: String? = null
) {
    val durationMs: Long get() = endUtc - startUtc
}

/** Sleep stages, ordered from "most awake" to "deepest" (the order they are stacked in the hypnogram). */
enum class SleepStage(val code: Int, val label: String) {
    AWAKE(1, "Awake"),
    REM(6, "REM"),
    LIGHT(4, "Light"),
    DEEP(5, "Deep"),
    /** Asleep, stage unknown. */
    SLEEPING(2, "Asleep");

    companion object {
        /** Maps Health Connect's `SleepSessionRecord.STAGE_TYPE_*` ints. */
        fun fromCode(code: Int): SleepStage = when (code) {
            1, 3, 7 -> AWAKE // awake, out of bed, awake in bed
            6 -> REM
            4 -> LIGHT
            5 -> DEEP
            else -> SLEEPING
        }
    }
}

/** Everything the activity panels draw for one time range. */
data class ActivityData(
    val heartRate: List<HeartRateEntity> = emptyList(),
    val steps: List<StepsEntity> = emptyList(),
    val sleep: List<SleepStageEntity> = emptyList(),
    val exercise: List<ExerciseEntity> = emptyList()
) {
    val isEmpty: Boolean get() = heartRate.isEmpty() && steps.isEmpty() && sleep.isEmpty() && exercise.isEmpty()

    /** Sleep rows grouped into sessions, oldest first. */
    val sleepSessions: List<SleepSession>
        get() = sleep.groupBy { it.sessionId }.values
            .map { rows -> SleepSession(rows.sortedBy { it.startUtc }) }
            .sortedBy { it.startUtc }
}

/** The stages of one sleep session. */
data class SleepSession(val stages: List<SleepStageEntity>) {
    val startUtc: Long get() = stages.first().sessionStartUtc
    val endUtc: Long get() = stages.first().sessionEndUtc
    val durationMs: Long get() = endUtc - startUtc
    /** Time actually asleep (everything but awake). */
    val asleepMs: Long get() = stages.filter { it.sleepStage != SleepStage.AWAKE }.sumOf { it.endUtc - it.startUtc }
    val hasStages: Boolean get() = stages.any { it.sleepStage != SleepStage.SLEEPING }
}

/**
 * One local day's activity numbers. Sleep belongs to the day the user woke up on (a session counts
 * where it ends), exercise to the day it started, steps and heart rate to the clock day.
 */
data class ActivityDayStats(
    val steps: Int,
    /** Time asleep (awake time excluded) of the sessions ending on this day. */
    val sleepMs: Long,
    val awakeMs: Long,
    /** Time per stage over those sessions (stages that never occurred are absent). */
    val stageMs: Map<SleepStage, Long>,
    val exerciseCount: Int,
    val exerciseMs: Long,
    val avgBpm: Int?,
    /** Mean of the lowest 5 % of the day's per-minute heart rates. */
    val restingBpm: Int?,
    val maxBpm: Int?
) {
    val hasSleep: Boolean get() = sleepMs > 0
    val hasHeartRate: Boolean get() = avgBpm != null

    companion object {
        /** Null when [data] holds nothing that belongs to the day [dayStartUtc, dayEndUtc). */
        fun from(data: ActivityData, dayStartUtc: Long, dayEndUtc: Long): ActivityDayStats? {
            val day = dayStartUtc until dayEndUtc
            val bpms = data.heartRate.filter { it.timestamp in day }.map { it.bpm }
            val steps = data.steps.filter { it.startUtc in day }.sumOf { it.count }
            val sessions = data.sleepSessions.filter { it.endUtc in day }
            val exercises = data.exercise.filter { it.startUtc in day }
            if (bpms.isEmpty() && steps == 0 && sessions.isEmpty() && exercises.isEmpty()) return null

            val stageMs = HashMap<SleepStage, Long>()
            sessions.flatMap { it.stages }.forEach { stageMs.merge(it.sleepStage, it.endUtc - it.startUtc, Long::plus) }
            val sorted = bpms.sorted()
            val lowest = sorted.take(maxOf(1, sorted.size / 20))
            return ActivityDayStats(
                steps = steps,
                sleepMs = stageMs.filterKeys { it != SleepStage.AWAKE }.values.sum(),
                awakeMs = stageMs[SleepStage.AWAKE] ?: 0L,
                stageMs = stageMs,
                exerciseCount = exercises.size,
                exerciseMs = exercises.sumOf { it.durationMs },
                avgBpm = bpms.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() },
                restingBpm = lowest.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() },
                maxBpm = sorted.lastOrNull()
            )
        }
    }
}

/** What the History overview draws per range: sleep and logged workouts, plus elevated-activity ranges found in heart rate and steps. */
data class ActivityOverview(
    val sessions: ActivityData = ActivityData(),
    val elevated: List<ActivitySpan> = emptyList(),
    val steps: List<StepsEntity> = emptyList()
)

/** Total length of the union of [intervals] (start to end, ms): overlapping ones count once. */
fun unionDurationMs(intervals: List<Pair<Long, Long>>): Long {
    var total = 0L
    var curStart = 0L
    var curEnd = Long.MIN_VALUE
    for ((s, e) in intervals.sortedBy { it.first }) {
        if (curEnd == Long.MIN_VALUE || s > curEnd) {
            if (curEnd != Long.MIN_VALUE) total += curEnd - curStart
            curStart = s; curEnd = e
        } else if (e > curEnd) curEnd = e
    }
    if (curEnd != Long.MIN_VALUE) total += curEnd - curStart
    return total
}
