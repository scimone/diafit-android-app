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
