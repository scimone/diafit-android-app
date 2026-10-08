package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.model.BasalSegment
import uk.scimone.diafit.core.domain.model.buildBasalTimeline
import uk.scimone.diafit.core.domain.model.toProfileSwitch
import uk.scimone.diafit.core.domain.repository.PumpEventRepository

/** The basal rate over a time range: the Profile Switch schedule with the loop's temp basals on top. */
class GetBasalTimelineUseCase(private val repository: PumpEventRepository) {
    suspend operator fun invoke(fromUtc: Long, toUtc: Long, userId: Int): List<BasalSegment> {
        val switches = repository.getBefore("Profile Switch", toUtc + 1, MAX_SWITCHES, userId).mapNotNull { it.toProfileSwitch() }
        // A temp basal enacted shortly before the range can still be running at its start.
        val temps = repository.getBetween(fromUtc - TEMP_LOOKBACK_MS, toUtc, userId).filter { it.eventType == "Temp Basal" }
        return buildBasalTimeline(switches, temps, fromUtc, toUtc)
    }

    private companion object {
        const val MAX_SWITCHES = 50
        const val TEMP_LOOKBACK_MS = 6 * 3_600_000L
    }
}
