package uk.scimone.diafit.journal.presentation.model

import uk.scimone.diafit.core.domain.model.ExerciseEntity
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.SleepSession
import uk.scimone.diafit.core.domain.model.SleepStage

/** A night's sleep (from Health Connect). It is listed at the time the user woke up. */
data class SleepEntryUi(
    override val id: Int,
    /** Wake-up time: the entry sits on the day the sleep ended. */
    override val timeUtc: Long,
    val startUtc: Long,
    val asleepMs: Long,
    /** Time per stage, in [SleepStage] order; only stages that occurred. */
    val stageMs: Map<SleepStage, Long>,
    val hasStages: Boolean
) : JournalEntryUi {
    override val kind: JournalEntryKind get() = JournalEntryKind.SLEEP
    val endUtc: Long get() = timeUtc
}

/** A workout (from Health Connect), with the heart rate it showed. */
data class ExerciseEntryUi(
    override val id: Int,
    override val timeUtc: Long,
    val endUtc: Long,
    val title: String,
    val avgBpm: Int?,
    val maxBpm: Int?
) : JournalEntryUi {
    override val kind: JournalEntryKind get() = JournalEntryKind.ACTIVITY
    val durationMs: Long get() = endUtc - timeUtc
}

fun SleepSession.toUi(): SleepEntryUi {
    val perStage = SleepStage.entries.associateWith { stage ->
        stages.filter { it.sleepStage == stage }.sumOf { it.endUtc - it.startUtc }
    }.filterValues { it > 0 }
    return SleepEntryUi(
        id = stages.first().id,
        timeUtc = endUtc,
        startUtc = startUtc,
        asleepMs = asleepMs,
        stageMs = perStage,
        hasStages = hasStages
    )
}

/** [heartRate]: the readings during the workout (any extra ones are ignored). */
fun ExerciseEntity.toUi(heartRate: List<HeartRateEntity>): ExerciseEntryUi {
    val during = heartRate.filter { it.timestamp in startUtc..endUtc }.map { it.bpm }
    return ExerciseEntryUi(
        id = id,
        timeUtc = startUtc,
        endUtc = endUtc,
        title = title ?: "Exercise",
        avgBpm = during.takeIf { it.isNotEmpty() }?.average()?.let { Math.round(it).toInt() },
        maxBpm = during.maxOrNull()
    )
}
