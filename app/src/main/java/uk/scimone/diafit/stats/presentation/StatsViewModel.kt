package uk.scimone.diafit.stats.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.data.healthconnect.HealthConnectManager
import uk.scimone.diafit.core.data.healthconnect.HealthConnectPermissions
import uk.scimone.diafit.core.data.healthconnect.HealthConnectScheduler
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.repository.SettingsRepository
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.presentation.SettingsChangeBus
import uk.scimone.diafit.stats.data.LoadStatsDaysUseCase
import uk.scimone.diafit.stats.domain.StatsDay
import uk.scimone.diafit.stats.domain.StatsPeriod
import uk.scimone.diafit.stats.domain.StatsWindow
import java.time.LocalDate

data class StatsState(
    val period: StatsPeriod = StatsPeriod.DAY,
    /** 0 = the current day / week / month / year, -1 the one before... */
    val offset: Int = 0,
    val window: StatsWindow = StatsWindow.of(StatsPeriod.DAY, LocalDate.now()),
    val days: List<StatsDay> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /** Health Connect is on but the heart-rate variability / calories permissions haven't been granted yet. */
    val needsVitalsPermission: Boolean = false
) {
    val canGoNewer: Boolean get() = offset < 0
}

class StatsViewModel(
    private val loadDays: LoadStatsDaysUseCase,
    private val getTargetRange: GetTargetRangeUseCase,
    private val settings: SettingsRepository,
    private val healthConnect: HealthConnectManager,
    private val healthConnectScheduler: HealthConnectScheduler,
    private val userId: Int
) : ViewModel() {

    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch { SettingsChangeBus.settingsChanged.collect { load() } }
        refreshPermissions()
    }

    fun setPeriod(period: StatsPeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period, offset = 0, window = StatsWindow.of(period, LocalDate.now()), isLoading = true) }
        load()
    }

    /** Jumps to the day / week / month / year containing [date] (a later date stops at the current one). */
    fun goTo(date: LocalDate) {
        val s = _state.value
        val offset = StatsWindow.offsetOf(s.period, LocalDate.now(), date)
        if (offset == s.offset) return
        _state.update { it.copy(offset = offset, window = StatsWindow.of(s.period, LocalDate.now(), offset), isLoading = true) }
        load()
    }

    fun older() = page(-1)
    fun newer() = page(+1)

    private fun page(delta: Int) {
        val s = _state.value
        val offset = (s.offset + delta).coerceAtMost(0)
        if (offset == s.offset) return
        _state.update { it.copy(offset = offset, window = StatsWindow.of(s.period, LocalDate.now(), offset), isLoading = true) }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val window = _state.value.window
            val thresholds = GlucoseThresholds.from(getTargetRange().toCore())
            // The current window keeps up with new readings; an older one never changes.
            while (isActive) {
                try {
                    val days = loadDays(userId, window.first, window.lastExclusive, thresholds)
                    _state.update { if (it.window == window) it.copy(days = days, isLoading = false, errorMessage = null) else it }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load stats", e)
                    _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Couldn't load stats") }
                }
                if (window.lastExclusive <= LocalDate.now()) break
                delay(REFRESH_MS)
            }
        }
    }

    /** Re-checks whether the extra Health Connect permissions are missing (they can change outside the app). */
    fun refreshPermissions() {
        viewModelScope.launch {
            val needs = settings.isHealthConnectEnabled() && healthConnect.isAvailable &&
                !HealthConnectPermissions.vitals.all { it in healthConnect.grantedPermissions() }
            _state.update { it.copy(needsVitalsPermission = needs) }
        }
    }

    /** The permission dialog closed: import what was just allowed (14 days back). */
    fun onVitalsPermissionResult() {
        refreshPermissions()
        healthConnectScheduler.syncNow(backfill = true)
    }

    private companion object {
        const val TAG = "StatsViewModel"
        const val REFRESH_MS = 60_000L
    }
}
