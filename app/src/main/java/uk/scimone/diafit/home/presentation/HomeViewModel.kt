package uk.scimone.diafit.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.usecase.GetAllCgmSinceUseCase
import uk.scimone.diafit.core.domain.usecase.GetLatestCgmUseCase
import uk.scimone.diafit.core.domain.util.nowMinusXMinutes
import uk.scimone.diafit.home.presentation.model.CgmEntityUi
import uk.scimone.diafit.home.presentation.model.toCgmEntityUi
import uk.scimone.diafit.home.presentation.model.toChartData
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.presentation.SettingsChangeBus

class HomeViewModel(
    private val getLatestCgmUseCase: GetLatestCgmUseCase,
    private val getAllCgmSinceUseCase: GetAllCgmSinceUseCase,
    private val getTargetRangeUseCase: GetTargetRangeUseCase,
    private val userId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState(isLoading = true))
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        loadAllData()

        viewModelScope.launch {
            SettingsChangeBus.settingsChanged.collect {
                loadAllData()
            }
        }
    }

    private fun loadAllData() {
        observeLatestCgm()
        observeCgmHistory()
        loadTargetRange()
    }

    private fun observeLatestCgm() {
        viewModelScope.launch {
            getLatestCgmUseCase()
                .catch { e ->
                    _state.value = HomeState(
                        error = e.message,
                        isLoading = false
                    )
                }
                .collect { cgm ->
                    _state.update {
                        it.copy(
                            cgmUi = cgm?.toCgmEntityUi(),
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun observeCgmHistory(nowMinus24h: Long = nowMinusXMinutes(24 * 60)) {
        viewModelScope.launch {
            getAllCgmSinceUseCase(nowMinus24h, userId)
                .catch { e ->
                    _state.update {
                        it.copy(
                            error = e.message,
                            isLoading = false
                        )
                    }
                }
                .collect { history ->
                    val cgmChartDataList = history.map { it.toChartData() }
                    _state.update {
                        it.copy(
                            cgmHistory = cgmChartDataList,
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun loadTargetRange() {
        viewModelScope.launch {
            try {
                val targetRange = getTargetRangeUseCase().toCore()
                _state.update {
                    it.copy(
                        targetRangeLower = targetRange.lowerBound,
                        targetRangeUpper = targetRange.upperBound
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        targetRangeLower = 70,
                        targetRangeUpper = 180
                    )
                }
            }
        }
    }
}
