package uk.scimone.diafit.journal.presentation.model

import uk.scimone.diafit.core.domain.model.BolusEntity

/** An insulin bolus that belongs to no meal, shown as its own journal entry. */
data class BolusEntryUi(val bolus: BolusEntity) : JournalEntryUi {
    override val id: Int get() = bolus.id
    override val kind: JournalEntryKind get() = JournalEntryKind.BOLUS
    override val timeUtc: Long get() = bolus.timestampUtc
}
