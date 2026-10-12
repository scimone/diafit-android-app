package uk.scimone.diafit.logs.presentation.model

import uk.scimone.diafit.core.domain.model.GlucoseEpisode

/** A stretch below or above the target range, shown as its own logs entry. */
data class GlucoseEpisodeUi(val episode: GlucoseEpisode) : LogsEntryUi {
    /** Start minute: unique enough for list keys, since two episodes can't start in the same minute. */
    override val id: Int get() = (episode.startUtc / 60_000L).toInt()
    override val kind: LogsEntryKind get() = LogsEntryKind.GLUCOSE
    override val timeUtc: Long get() = episode.startUtc
}
