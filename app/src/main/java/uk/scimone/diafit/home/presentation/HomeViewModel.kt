package uk.scimone.diafit.home.presentation

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.usecase.GetAllCgmSinceUseCase
import uk.scimone.diafit.core.domain.usecase.GetLatestCgmUseCase
import uk.scimone.diafit.core.domain.util.nowMinusXMinutes
import uk.scimone.diafit.home.presentation.model.toCgmEntityUi
import uk.scimone.diafit.home.presentation.model.toChartData
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.presentation.SettingsChangeBus

import kotlinx.coroutines.delay
import uk.scimone.diafit.core.domain.usecase.GetAllMealsSinceUseCase
import uk.scimone.diafit.home.presentation.model.toMealEntityUi

class HomeViewModel(
    private val getLatestCgmUseCase: GetLatestCgmUseCase,
    private val getAllCgmSinceUseCase: GetAllCgmSinceUseCase,
    private val getTargetRangeUseCase: GetTargetRangeUseCase,
    private val getAllMealsSinceUseCase: GetAllMealsSinceUseCase,
    private val application: Application,
    private val userId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState(isLoading = true))
    val state: StateFlow<HomeState> = _state.asStateFlow()

    // Store latest CgmEntity separately to update timeSince
    private var latestCgmEntity: CgmEntity? = null

    init {
        loadAllData()

        viewModelScope.launch {
            SettingsChangeBus.settingsChanged.collect {
                loadAllData()
            }
        }

        startCountdownUpdater()
    }

    private fun loadAllData() {
        observeLatestCgm()
        observeCgmHistory()
        observeMealHistory()
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
                    latestCgmEntity = cgm
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

    private fun observeMealHistory(nowMinus24h: Long = nowMinusXMinutes(24 * 60)) {
        viewModelScope.launch {
            getAllMealsSinceUseCase(nowMinus24h, userId)
                .catch { e ->
                    _state.update {
                        it.copy(
                            error = e.message,
                            isLoading = false
                        )
                    }
                }
                .collect { meals ->
                    val mealUiList = meals.map { it.toMealEntityUi(application.applicationContext) }
                    _state.update {
                        it.copy(
                            mealHistory = mealUiList,
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

    private fun startCountdownUpdater() {
        viewModelScope.launch {
            while (true) {
                latestCgmEntity?.let { cgm ->
                    _state.update {
                        it.copy(
                            cgmUi = cgm.toCgmEntityUi(), // this recalculates "timeSince"
                            isLoading = false
                        )
                    }
                }
                delay(1000L)  // update every second (adjust if needed)
            }
        }
    }
}
