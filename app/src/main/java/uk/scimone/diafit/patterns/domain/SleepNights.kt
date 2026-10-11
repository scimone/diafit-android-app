package uk.scimone.diafit.patterns.domain

import uk.scimone.diafit.core.domain.model.SleepSession
import uk.scimone.diafit.core.domain.model.SleepStage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The main sleep of one night, filed under the day the person woke up. */
data class SleepNight(
    val wakeDate: LocalDate,
    val startUtc: Long,
    val endUtc: Long,
    val asleepMin: Int,
    /** Share of the night spent awake, when the source reports stages; null otherwise. */
    val awakeShare: Double?,
    /** Minutes after 18:00 local time at which the person went to bed (so 01:00 comes after 23:00). */
    val bedtimeMin: Int
)

object SleepNights {
    /** Sessions shorter than this are naps, not a night. */
    private const val MIN_NIGHT_MS = 3 * 3_600_000L

    /** One night per wake-up day (the longest session ending that day), oldest first. */
    fun build(sessions: List<SleepSession>, zone: ZoneId): List<SleepNight> =
        sessions.filter { it.durationMs >= MIN_NIGHT_MS && it.asleepMs > 0 }
            .groupBy { Instant.ofEpochMilli(it.endUtc).atZone(zone).toLocalDate() }
            .map { (date, list) ->
                val s = list.maxBy { it.durationMs }
                val start = Instant.ofEpochMilli(s.startUtc).atZone(zone)
                val awakeMs = s.stages.filter { it.sleepStage == SleepStage.AWAKE }.sumOf { it.endUtc - it.startUtc }
                SleepNight(
                    date, s.startUtc, s.endUtc, (s.asleepMs / 60_000L).toInt(),
                    if (s.hasStages) awakeMs.toDouble() / s.durationMs else null,
                    (start.hour * 60 + start.minute - 18 * 60 + 1440) % 1440
                )
            }.sortedBy { it.wakeDate }
}
