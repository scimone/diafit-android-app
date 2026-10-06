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
import uk.scimone.diafit.settings.domain.model.AiConfig
import uk.scimone.diafit.settings.domain.model.BolusSource
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.model.NightscoutConfig
import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange
import uk.scimone.diafit.settings.domain.usecase.GetAiConfigUseCase
import uk.scimone.diafit.settings.domain.usecase.GetBolusSourceUseCase
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase
import uk.scimone.diafit.settings.domain.usecase.GetNightscoutConfigUseCase
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.domain.usecase.SetAiConfigUseCase
import uk.scimone.diafit.settings.domain.usecase.SetBolusSourceUseCase
import uk.scimone.diafit.settings.domain.usecase.SetCgmSourceUseCase
import uk.scimone.diafit.settings.domain.usecase.SetNightscoutConfigUseCase
import uk.scimone.diafit.settings.domain.usecase.SetTargetRangeUseCase
import uk.scimone.diafit.settings.isIgnoringBatteryOptimizations

class SettingsViewModel(
    private val getCgmSource: GetCgmSourceUseCase,
    private val setCgmSource: SetCgmSourceUseCase,
    private val getBolusSource: GetBolusSourceUseCase,
    private val setBolusSource: SetBolusSourceUseCase,
    private val getGlucoseTargetRange: GetTargetRangeUseCase,
    private val setGlucoseTargetRange: SetTargetRangeUseCase,
    private val getNightscoutConfig: GetNightscoutConfigUseCase,
    private val setNightscoutConfig: SetNightscoutConfigUseCase,
    private val getAiConfig: GetAiConfigUseCase,
    private val setAiConfig: SetAiConfigUseCase,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    private val _restartCgmServiceEvent = MutableSharedFlow<CgmSource>()
    private val _restartBolusServiceEvent = MutableSharedFlow<BolusSource>()
    val restartCgmServiceEvent = _restartCgmServiceEvent.asSharedFlow()
    val restartBolusServiceEvent = _restartBolusServiceEvent.asSharedFlow()

    init {
        refreshSettings()
    }

    private fun refreshSettings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val batteryIgnored = appContext.isIgnoringBatteryOptimizations()
            val cgmSource = getCgmSource()
            val bolusSource = getBolusSource()
            val range = getGlucoseTargetRange()
            val nightscoutConfig = getNightscoutConfig()
            val aiConfig = getAiConfig()

            _state.value = _state.value.copy(
                selectedCgmSource = cgmSource,
                selectedBolusSource = bolusSource,
                glucoseTargetRange = range,
                nightscoutConfig = nightscoutConfig,
                aiConfig = aiConfig,
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

    fun onBolusSourceSelected(source: BolusSource) {
        viewModelScope.launch {
            setBolusSource(source)
            _state.value = _state.value.copy(selectedBolusSource = source)
            _restartBolusServiceEvent.emit(source) // side effect event
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

    fun onNightscoutConfigChanged(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            val newConfig = NightscoutConfig(baseUrl, apiKey)
            setNightscoutConfig(newConfig)
            _state.value = _state.value.copy(nightscoutConfig = newConfig)
            SettingsChangeBus.notifyChange()
        }
    }

    fun onAiConfigChanged(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            val newConfig = AiConfig(baseUrl, apiKey)
            setAiConfig(newConfig)
            _state.value = _state.value.copy(aiConfig = newConfig)
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
