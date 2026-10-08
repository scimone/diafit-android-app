package uk.scimone.diafit.devices.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import uk.scimone.diafit.core.data.nightscout.DeviceLifetimeStore
import uk.scimone.diafit.core.data.nightscout.DeviceStatusStore
import uk.scimone.diafit.core.domain.model.DeviceAge
import uk.scimone.diafit.core.domain.model.DeviceStatus
import uk.scimone.diafit.core.domain.model.deviceAges
import uk.scimone.diafit.core.domain.repository.PumpEventRepository

data class DevicesUiState(
    val loaded: Boolean = false,
    val ages: List<DeviceAge> = emptyList(),
    val status: DeviceStatus? = null,
    val nowUtc: Long = System.currentTimeMillis()
)

/** Age / estimated expiry per consumable (from the stored change events) plus the latest Nightscout levels. */
class DevicesViewModel(
    private val pumpEvents: PumpEventRepository,
    statusStore: DeviceStatusStore,
    lifetimeStore: DeviceLifetimeStore,
    private val userId: Int
) : ViewModel() {

    // Re-reads every minute and whenever an event is added or removed.
    private val events = combine(
        pumpEvents.observeCount(userId),
        flow { while (true) { emit(System.currentTimeMillis()); delay(60_000) } }
    ) { _, now -> now }

    val state: StateFlow<DevicesUiState> = combine(events, statusStore.status, lifetimeStore.lifetimes) { now, status, lifetimes ->
        val recent = pumpEvents.getBetween(now - LOOKBACK_MS, now, userId).filter { it.isMilestone }
        DevicesUiState(loaded = true, ages = deviceAges(recent, now, lifetimes, batteryReported = status?.let { it.pumpBatteryPercent != null || it.pumpBatteryVolt != null } == true), status = status, nowUtc = now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesUiState())

    private companion object { const val LOOKBACK_MS = 60L * 24 * 3_600_000 }
}
