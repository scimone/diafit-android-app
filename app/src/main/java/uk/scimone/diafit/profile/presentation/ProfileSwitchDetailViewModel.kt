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
            val event = repository.getById(eventId)
            val asSwitch = event?.toProfileSwitch()
            if (asSwitch != null) {
                val previous = repository.getBefore("Profile Switch", asSwitch.startUtc, 10, userId).mapNotNull { e -> e.toProfileSwitch() }
                    .firstOrNull { sw -> sw.profile != null }
                val nearby = repository.getBetween(asSwitch.startUtc - TARGET_PAIR_WINDOW_MS, asSwitch.startUtc + TARGET_PAIR_WINDOW_MS, userId)
                val target = pairTemporaryTargets(nearby + listOfNotNull(event).filter { e -> nearby.none { it.id == e.id } })[eventId]
                targetEventId = target?.id
                _state.update { ProfileSwitchDetailState(loaded = true, current = asSwitch, previous = previous, target = target?.toTemporaryTarget()) }
            } else {
                // A target without a profile switch: the profile in force at that time gives the "before" target.
                val target = event?.toTemporaryTarget()
                val context = target?.let {
                    repository.getBefore("Profile Switch", it.startUtc + 1, 10, userId).mapNotNull { e -> e.toProfileSwitch() }.firstOrNull { sw -> sw.profile != null }
                }
                _state.update { ProfileSwitchDetailState(loaded = true, previous = context, target = target) }
            }
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
    /** The switch before this one (for a target-only entry: the switch whose profile was in force). */
    val previous: ProfileSwitch? = null,
    /** The temporary target that came with this switch, if any. */
    val target: TemporaryTarget? = null
)
