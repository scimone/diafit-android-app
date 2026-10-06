package uk.scimone.diafit.journal.presentation.model

import uk.scimone.diafit.core.domain.model.GlucoseEpisode

/** A stretch below or above the target range, shown as its own journal entry. */
data class GlucoseEpisodeUi(val episode: GlucoseEpisode) : JournalEntryUi {
    /** Start minute: unique enough for list keys, since two episodes can't start in the same minute. */
    override val id: Int get() = (episode.startUtc / 60_000L).toInt()
    override val kind: JournalEntryKind get() = JournalEntryKind.GLUCOSE
    override val timeUtc: Long get() = episode.startUtc
}
