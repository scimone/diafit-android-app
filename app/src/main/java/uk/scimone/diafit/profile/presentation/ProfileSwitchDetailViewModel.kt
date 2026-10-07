package uk.scimone.diafit.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.ProfileSwitch
import uk.scimone.diafit.core.domain.model.toProfileSwitch
import uk.scimone.diafit.core.domain.repository.PumpEventRepository

/** One Profile Switch and the switch before it, for the before/after page. */
class ProfileSwitchDetailViewModel(
    private val repository: PumpEventRepository,
    private val userId: Int,
    private val eventId: Int
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileSwitchDetailState())
    val state: StateFlow<ProfileSwitchDetailState> = _state

    init {
        viewModelScope.launch {
            val current = repository.getById(eventId)?.toProfileSwitch()
            val previous = current?.let {
                repository.getBefore("Profile Switch", it.startUtc, 10, userId).mapNotNull { e -> e.toProfileSwitch() }
                    .firstOrNull { sw -> sw.profile != null }
            }
            _state.update { ProfileSwitchDetailState(loaded = true, current = current, previous = previous) }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.setDeleted(eventId, true)
            onDone()
        }
    }
}

data class ProfileSwitchDetailState(
    val loaded: Boolean = false,
    val current: ProfileSwitch? = null,
    /** The newest earlier switch that carries a profile, if any. */
    val previous: ProfileSwitch? = null
)
