package uk.scimone.diafit.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.repository.CgmRepository
import uk.scimone.diafit.home.presentation.model.toCgmEntityUi
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.util.nowMinusXMinutes
import uk.scimone.diafit.home.presentation.model.toChartData

class HomeViewModel(
    private val cgmRepository: CgmRepository,
    private val userId: Int,
) : ViewModel() {

    private val latestCgm = MutableStateFlow<CgmEntity?>(null)

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        observeLatestCgm()
        observeCgmHistory()
        startTicker()
    }

    private fun observeLatestCgm() {
        viewModelScope.launch {
            cgmRepository.getLatestCgm()
                .catch { e ->
                    _state.value = HomeState(
                        error = e.message,
                        isLoading = false
                    )
                }
                .collect { cgm ->
                    latestCgm.value = cgm
                }
        }
    }

    private fun observeCgmHistory(
        nowMinus24h: Long = nowMinusXMinutes(24 * 60)
    ) {
        viewModelScope.launch {
            cgmRepository.getAllCgmSince(nowMinus24h, userId)
                .catch { e ->
                    _state.value = HomeState(
                        error = e.message,
                        isLoading = false
                    )
                }
                .collect { history ->
                    val cgmChartDataList = history.map { cgmEntity ->
                        cgmEntity.toChartData()
                    }
                    _state.value = _state.value.copy(cgmHistory = cgmChartDataList)
                }
        }
    }

    private fun startTicker() {
        viewModelScope.launch {
            while (true) {
                latestCgm.value?.let { cgm ->
                    _state.value = _state.value.copy(
                        cgmUi = cgm.toCgmEntityUi(),
                        isLoading = false
                    )
                }
                delay(1000L) // Tick every second
            }
        }
    }
}
