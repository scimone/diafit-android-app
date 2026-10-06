package uk.scimone.diafit.history.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.scimone.diafit.history.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.domain.usecase.ClusterTreatmentsUseCase
import uk.scimone.diafit.history.domain.usecase.GetDailyHistoryUseCase
import uk.scimone.diafit.history.presentation.model.DayHistoryUi
import uk.scimone.diafit.history.presentation.model.toThresholds
import uk.scimone.diafit.history.presentation.model.toUi
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.presentation.SettingsChangeBus

class HistoryViewModel(
    private val getDailyHistory: GetDailyHistoryUseCase,
    private val clusterTreatments: ClusterTreatmentsUseCase,
    private val getTargetRange: GetTargetRangeUseCase,
    private val userId: Int
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState(isLoading = true))
    val state: StateFlow<HistoryState> = _state

    private var observeJob: Job? = null
    private var page = 0

    init {
        observe()
        // The target range is a setting: redraw when it changes.
        viewModelScope.launch { SettingsChangeBus.settingsChanged.collect { observe() } }
    }

    /** Pages are two-week windows; 0 is the latest, higher is older. */
    fun showOlder() = setPage(page + 1)

    fun showNewer() = setPage((page - 1).coerceAtLeast(0))

    private fun setPage(newPage: Int) {
        if (newPage == page) return
        page = newPage
        _state.update { it.copy(isLoading = true) }
        observe()
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            val target = getTargetRange().toCore()
            getDailyHistory(userId, HISTORY_DAYS, page)
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    Log.e(TAG, "Failed to load history", e)
                    _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load history") }
                }
                .collect { days ->
                    val ui = days.map { it.toUi(target, clusterTreatments) }
                    _state.update {
                        HistoryState(days = ui, thresholds = target.toThresholds(), page = page, isLoading = false)
                    }
                }
        }
    }

    private companion object {
        const val TAG = "HistoryViewModel"
        const val HISTORY_DAYS = 14
    }
}

data class HistoryState(
    val days: List<DayHistoryUi> = emptyList(),
    val thresholds: GlucoseThresholds = GlucoseThresholds(low = 70, high = 180),
    val page: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
