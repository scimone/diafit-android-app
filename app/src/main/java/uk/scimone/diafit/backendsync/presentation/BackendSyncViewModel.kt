package uk.scimone.diafit.backendsync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.scimone.diafit.backendsync.data.BackendApi
import uk.scimone.diafit.backendsync.data.BackendConfig
import uk.scimone.diafit.backendsync.data.BackendResult
import uk.scimone.diafit.backendsync.data.BackendSyncScheduler
import uk.scimone.diafit.backendsync.data.BackendSyncStatus
import uk.scimone.diafit.backendsync.data.BackendSyncStore

sealed interface BackendCheck {
    data object Idle : BackendCheck
    data object Checking : BackendCheck
    data class Ok(val username: String) : BackendCheck
    data class Failed(val message: String) : BackendCheck
}

class BackendSyncViewModel(
    private val store: BackendSyncStore,
    private val api: BackendApi,
    private val scheduler: BackendSyncScheduler
) : ViewModel() {
    val config: StateFlow<BackendConfig> = store.config
    val status: StateFlow<BackendSyncStatus> = store.status

    private val _check = MutableStateFlow<BackendCheck>(BackendCheck.Idle)
    val check: StateFlow<BackendCheck> = _check.asStateFlow()

    /** Checks the address and token against `/me` first; only a working connection is saved and synced. */
    fun saveAndTest(baseUrl: String, token: String) {
        val candidate = BackendConfig(baseUrl.trim(), token.trim())
        if (!candidate.isConfigured) {
            _check.value = BackendCheck.Failed("Enter the address and the API token")
            return
        }
        _check.value = BackendCheck.Checking
        viewModelScope.launch {
            when (val result = api.me(candidate)) {
                is BackendResult.Ok -> {
                    store.setConfig(candidate)
                    _check.value = BackendCheck.Ok(result.data.username)
                    scheduler.schedulePeriodic()
                    scheduler.syncNow()
                }
                is BackendResult.Failed -> _check.value = BackendCheck.Failed(result.message)
            }
        }
    }

    fun test() {
        val current = store.config.value
        if (!current.isConfigured) return
        _check.value = BackendCheck.Checking
        viewModelScope.launch {
            _check.value = when (val result = api.me(current)) {
                is BackendResult.Ok -> BackendCheck.Ok(result.data.username)
                is BackendResult.Failed -> BackendCheck.Failed(result.message)
            }
        }
    }

    fun syncNow() = scheduler.syncNow()

    /** Stops syncing; what was uploaded stays on the backend. */
    fun disconnect() {
        scheduler.cancel()
        store.setConfig(BackendConfig())
        _check.value = BackendCheck.Idle
    }
}
