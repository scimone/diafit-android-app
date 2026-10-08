package uk.scimone.diafit.devices.presentation

import uk.scimone.diafit.core.domain.model.PumpEventEntity
import uk.scimone.diafit.core.domain.repository.BolusRepository
import uk.scimone.diafit.core.domain.repository.PumpEventRepository

/** The recent device events and the pump type AAPS reported on its latest bolus (null when unknown). */
data class DeviceInputs(val events: List<PumpEventEntity>, val pumpType: String?)

/** Loads what [uk.scimone.diafit.core.domain.model.deviceLayout] / `deviceAges` need, for the Devices page and Settings. */
class DeviceInputsSource(private val pumpEvents: PumpEventRepository, private val boluses: BolusRepository) {
    suspend fun load(userId: Int, now: Long): DeviceInputs {
        val from = now - LOOKBACK_MS
        val pumpType = boluses.getBolusBetween(from, now, userId)
            .filter { it.pumpType != "AAPS" } // "AAPS" is the placeholder when no pumpType was sent
            .maxByOrNull { it.timestampUtc }?.pumpType
        return DeviceInputs(pumpEvents.getBetween(from, now, userId), pumpType)
    }

    private companion object { const val LOOKBACK_MS = 60L * 24 * 3_600_000 }
}
