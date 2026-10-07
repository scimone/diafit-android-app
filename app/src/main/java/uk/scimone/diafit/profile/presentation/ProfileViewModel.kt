package uk.scimone.diafit.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import uk.scimone.diafit.core.domain.model.ProfileSwitch
import uk.scimone.diafit.core.domain.model.toProfileSwitch
import uk.scimone.diafit.core.domain.repository.PumpEventRepository

/**
 * The profile AAPS is running, taken from its latest Profile Switch event. Read-only: it only ever
 * reflects what AAPS sent. [ProfileUiState.nowUtc] ticks every 30 s so "ended 10 min ago" stays right.
 */
class ProfileViewModel(
    pumpEventRepository: PumpEventRepository,
    userId: Int
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }

    val state: StateFlow<ProfileUiState> = combine(
        pumpEventRepository.observeLatest(PROFILE_SWITCH, RECENT_SWITCHES, userId),
        ticker
    ) { events, now ->
        ProfileUiState(loaded = true, switches = events.mapNotNull { it.toProfileSwitch() }, nowUtc = now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    private companion object {
        const val PROFILE_SWITCH = "Profile Switch"
        const val RECENT_SWITCHES = 8
    }
}

data class ProfileUiState(
    val loaded: Boolean = false,
    /** Newest first. */
    val switches: List<ProfileSwitch> = emptyList(),
    val nowUtc: Long = System.currentTimeMillis()
) {
    /** The newest switch that carries a profile. */
    val latest: ProfileSwitch? get() = switches.firstOrNull { it.profile != null }
}
