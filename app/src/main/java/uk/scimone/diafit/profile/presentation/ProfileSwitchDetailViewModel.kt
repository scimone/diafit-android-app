package uk.scimone.diafit.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.ProfileSwitch
import uk.scimone.diafit.core.domain.model.TARGET_PAIR_WINDOW_MS
import uk.scimone.diafit.core.domain.model.TemporaryTarget
import uk.scimone.diafit.core.domain.model.pairTemporaryTargets
import uk.scimone.diafit.core.domain.model.toTemporaryTarget
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
            val target = current?.let {
                repository.getBetween(it.startUtc - TARGET_PAIR_WINDOW_MS, it.startUtc + TARGET_PAIR_WINDOW_MS, userId)
                    .let { events -> pairTemporaryTargets(events + listOfNotNull(repository.getById(eventId)).filter { e -> events.none { x -> x.id == e.id } }) }[eventId]
            }
            targetEventId = target?.id
            _state.update { ProfileSwitchDetailState(loaded = true, current = current, previous = previous, target = target?.toTemporaryTarget()) }
        }
    }

    private var targetEventId: Int? = null

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.setDeleted(eventId, true)
            targetEventId?.let { repository.setDeleted(it, true) }
            onDone()
        }
    }
}

data class ProfileSwitchDetailState(
    val loaded: Boolean = false,
    val current: ProfileSwitch? = null,
    /** The newest earlier switch that carries a profile, if any. */
    val previous: ProfileSwitch? = null,
    /** The temporary target that came with this switch, if any. */
    val target: TemporaryTarget? = null
)
