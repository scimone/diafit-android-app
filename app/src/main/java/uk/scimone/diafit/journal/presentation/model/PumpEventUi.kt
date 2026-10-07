package uk.scimone.diafit.journal.presentation.model

import uk.scimone.diafit.core.domain.model.PumpEventEntity
import uk.scimone.diafit.core.domain.model.summary

/** A device or therapy milestone from AAPS (pod/site change, profile switch, note, ...). */
data class PumpEventUi(
    override val id: Int,
    override val timeUtc: Long,
    val title: String,
    val notes: String?
) : JournalEntryUi {
    override val kind: JournalEntryKind get() = JournalEntryKind.DEVICE

    /** Opens the before/after detail page instead of the plain dialog. */
    val isProfileSwitch: Boolean get() = title.equals("Profile Switch", ignoreCase = true)
}

fun PumpEventEntity.toUi() = PumpEventUi(id, timestampUtc, eventType, summary())
