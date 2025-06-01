package uk.scimone.diafit.settings.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.domain.usecase.SetCgmSourceUseCase
import uk.scimone.diafit.settings.domain.usecase.SetTargetRangeUseCase
import uk.scimone.diafit.settings.isIgnoringBatteryOptimizations

class SettingsViewModel(
    private val getCgmSource: GetCgmSourceUseCase,
    private val setCgmSource: SetCgmSourceUseCase,
    private val getGlucoseTargetRange: GetTargetRangeUseCase,
    private val setGlucoseTargetRange: SetTargetRangeUseCase,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    private val _restartCgmServiceEvent = MutableSharedFlow<CgmSource>()
    val restartCgmServiceEvent = _restartCgmServiceEvent.asSharedFlow()

    init {
        refreshSettings()
    }

    private fun refreshSettings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val batteryIgnored = appContext.isIgnoringBatteryOptimizations()
            val source = getCgmSource()
            val range = getGlucoseTargetRange()

            _state.value = _state.value.copy(
                selectedCgmSource = source,
                glucoseTargetRange = range,
                isBatteryOptimizationIgnored = batteryIgnored,
                isLoading = false
            )
        }
    }

    fun onCgmSourceSelected(source: CgmSource) {
        viewModelScope.launch {
            setCgmSource(source)
            _state.value = _state.value.copy(selectedCgmSource = source)
            _restartCgmServiceEvent.emit(source) // side effect event
        }
    }

    fun onGlucoseTargetRangeChanged(lower: Int, upper: Int) {
        viewModelScope.launch {
            val newRange = SettingsGlucoseTargetRange(lower, upper)
            setGlucoseTargetRange(newRange)
            _state.value = _state.value.copy(glucoseTargetRange = newRange)
            SettingsChangeBus.notifyChange()
        }
    }

    fun checkBatteryOptimization() {
        viewModelScope.launch {
            val ignored = appContext.isIgnoringBatteryOptimizations()
            Log.d("SettingsViewModel", "Battery optimization ignored? $ignored")
            _state.value = _state.value.copy(isBatteryOptimizationIgnored = ignored)
        }
    }

}
