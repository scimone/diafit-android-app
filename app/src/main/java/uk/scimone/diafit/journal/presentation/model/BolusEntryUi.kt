package uk.scimone.diafit.journal.presentation.model

import uk.scimone.diafit.core.domain.model.BolusEntity

/** One delivered dose inside a [BolusEntryUi]. */
data class BolusPartUi(val timeUtc: Long, val units: Double, val isSmb: Boolean)

/**
 * Insulin that belongs to no meal, as its own journal entry: one bolus, or several given within
 * [BOLUS_GROUP_GAP_MS] of each other (at most [BOLUS_GROUP_MAX_SPAN_MS] in total) summed up, with every
 * dose kept in [parts] so the card can be expanded.
 */
data class BolusEntryUi(
    override val id: Int,
    /** Time of the latest dose. */
    override val timeUtc: Long,
    val units: Double,
    val count: Int,
    /** True when every dose was an automatic micro-bolus. */
    val isSmb: Boolean,
    val startUtc: Long = timeUtc,
    val parts: List<BolusPartUi> = emptyList()
) : JournalEntryUi {
    override val kind: JournalEntryKind get() = JournalEntryKind.BOLUS
}

const val BOLUS_GROUP_GAP_MS = 30 * 60_000L
const val BOLUS_GROUP_MAX_SPAN_MS = 60 * 60_000L

/** Boluses close together become one entry; a lone bolus stays its own entry. */
fun List<BolusEntity>.toBolusEntries(): List<BolusEntryUi> {
    val groups = mutableListOf<MutableList<BolusEntity>>()
    for (b in sortedBy { it.timestampUtc }) {
        val current = groups.lastOrNull()
        if (current != null &&
            b.timestampUtc - current.last().timestampUtc <= BOLUS_GROUP_GAP_MS &&
            b.timestampUtc - current.first().timestampUtc <= BOLUS_GROUP_MAX_SPAN_MS
        ) current += b else groups += mutableListOf(b)
    }
    return groups.map { g ->
        BolusEntryUi(
            // Negative for a group so it can't collide with a single bolus' row id.
            id = if (g.size == 1) g.first().id else -g.first().id - 1,
            timeUtc = g.last().timestampUtc,
            units = g.sumOf { it.value.toDouble() },
            count = g.size,
            isSmb = g.all { it.isSmb },
            startUtc = g.first().timestampUtc,
            parts = g.map { BolusPartUi(it.timestampUtc, it.value.toDouble(), it.isSmb) }
        )
    }
}
