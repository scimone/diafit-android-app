package uk.scimone.diafit.core.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.repository.BolusRepository
import uk.scimone.diafit.core.domain.repository.CgmRepository

/** CGM readings and insulin around a journal event, for the "how did this affect my glucose" view. */
data class GlucoseResponse(
    val windowStartUtc: Long,
    val windowEndUtc: Long,
    val readings: List<CgmEntity>,
    val boluses: List<BolusEntity>,
    /** Reading closest to the event time (within 15 min), if any. */
    val atEvent: CgmEntity?,
    /** Highest reading after the event, inside the window. */
    val peak: CgmEntity?,
    /** Lowest reading after the event, inside the window. */
    val low: CgmEntity?
)

class GetGlucoseResponseUseCase(
    private val cgmRepository: CgmRepository,
    private val bolusRepository: BolusRepository
) {
    /**
     * @param userId owner of the readings
     * @param eventTimeUtc when the event happened
     * @param durationMinutes how long the event is expected to influence glucose
     */
    suspend operator fun invoke(userId: Int, eventTimeUtc: Long, durationMinutes: Int): GlucoseResponse = withContext(Dispatchers.IO) {
        val start = eventTimeUtc - LEAD_IN_MS
        val end = eventTimeUtc + durationMinutes * 60_000L + TAIL_MS
        val readings = cgmRepository.getEntriesBetween(start, end, userId).sortedBy { it.timestamp }
        val boluses = bolusRepository.getBolusBetween(start, end, userId)

        val atEvent = readings
            .minByOrNull { kotlin.math.abs(it.timestamp - eventTimeUtc) }
            ?.takeIf { kotlin.math.abs(it.timestamp - eventTimeUtc) <= 15 * 60_000L }
        val after = readings.filter { it.timestamp >= eventTimeUtc }
        GlucoseResponse(
            windowStartUtc = start,
            windowEndUtc = end,
            readings = readings,
            boluses = boluses,
            atEvent = atEvent,
            peak = after.maxByOrNull { it.valueMgdl },
            low = after.minByOrNull { it.valueMgdl }
        )
    }

    private companion object {
        const val LEAD_IN_MS = 45 * 60_000L
        const val TAIL_MS = 60 * 60_000L
    }
}
