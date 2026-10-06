package uk.scimone.diafit.journal.presentation.model

import uk.scimone.diafit.core.domain.model.BolusEntity
import java.time.Instant
import java.time.ZoneId

/**
 * Insulin that belongs to no meal, as its own journal entry: one manual bolus, or all the automatic
 * micro-boluses (SMBs) of one clock hour ([count] > 1 possible, [isSmb] true) summed up.
 */
data class BolusEntryUi(
    override val id: Int,
    override val timeUtc: Long,
    val units: Double,
    val count: Int,
    val isSmb: Boolean,
    /** Start of the clock hour an SMB group covers; equals [timeUtc] for a manual bolus. */
    val hourStartUtc: Long = timeUtc
) : JournalEntryUi {
    override val kind: JournalEntryKind get() = JournalEntryKind.BOLUS
}

/** Manual boluses one by one, SMBs summed per local clock hour. */
fun List<BolusEntity>.toBolusEntries(zone: ZoneId = ZoneId.systemDefault()): List<BolusEntryUi> {
    val (smbs, manual) = partition { it.isSmb }
    val manualEntries = manual.map { BolusEntryUi(it.id, it.timestampUtc, it.value.toDouble(), 1, isSmb = false) }
    val smbEntries = smbs.groupBy { Instant.ofEpochMilli(it.timestampUtc).atZone(zone).truncatedTo(java.time.temporal.ChronoUnit.HOURS).toInstant().toEpochMilli() }
        .map { (hourStart, group) ->
            BolusEntryUi(
                id = -(hourStart / 60_000L).toInt(),  // negative: can't collide with a manual bolus' row id
                timeUtc = group.maxOf { it.timestampUtc },
                units = group.sumOf { it.value.toDouble() },
                count = group.size,
                isSmb = true,
                hourStartUtc = hourStart
            )
        }
    return manualEntries + smbEntries
}
