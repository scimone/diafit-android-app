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
import uk.scimone.diafit.core.data.healthconnect.HealthConnectImportSummary
import uk.scimone.diafit.core.data.healthconnect.HealthConnectManager
import uk.scimone.diafit.core.data.healthconnect.HealthConnectPermissions
import uk.scimone.diafit.core.data.healthconnect.HealthConnectScheduler
import uk.scimone.diafit.core.data.healthconnect.HealthConnectSyncer
import uk.scimone.diafit.settings.domain.repository.SettingsRepository
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
import uk.scimone.diafit.settings.domain.usecase.ListAiModelsUseCase
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
    private val listAiModels: ListAiModelsUseCase,
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val healthConnectManager: HealthConnectManager,
    private val healthConnectSyncer: HealthConnectSyncer,
    private val healthConnectScheduler: HealthConnectScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    private val _restartCgmServiceEvent = MutableSharedFlow<CgmSource>()
    private val _restartBolusServiceEvent = MutableSharedFlow<BolusSource>()
    val restartCgmServiceEvent = _restartCgmServiceEvent.asSharedFlow()
    val restartBolusServiceEvent = _restartBolusServiceEvent.asSharedFlow()

    init {
        refreshSettings()
        viewModelScope.launch {
            healthConnectSyncer.status.collect { sync ->
                _state.value = _state.value.let { it.copy(healthConnect = it.healthConnect.copy(sync = sync)) }
                // A finished import updates "last imported".
                if (sync is uk.scimone.diafit.core.data.healthconnect.HealthConnectSyncStatus.Idle) refreshHealthConnect()
            }
        }
    }

    /** Re-reads Health Connect's availability and which permissions are granted (they can change outside the app). */
    fun refreshHealthConnect() {
        viewModelScope.launch {
            val granted = healthConnectManager.grantedPermissions()
            val enabled = settingsRepository.isHealthConnectEnabled()
            val lastSync = settingsRepository.getHealthConnectLastSync()
            _state.value = _state.value.let {
                it.copy(
                    healthConnect = it.healthConnect.copy(
                        availability = healthConnectManager.availability(),
                        enabled = enabled,
                        activityGranted = HealthConnectPermissions.activity.all { p -> p in granted },
                        glucoseGranted = HealthConnectPermissions.glucose.all { p -> p in granted },
                        backgroundGranted = HealthConnectPermissions.BACKGROUND in granted,
                        lastSync = lastSync,
                        summary = HealthConnectImportSummary.decode(settingsRepository.getHealthConnectSummary())
                    )
                )
            }
        }
    }

    /** Result of the activity permission dialog: when everything was granted, switch the import on and backfill. */
    fun onActivityPermissionsResult() {
        viewModelScope.launch {
            val granted = healthConnectManager.grantedPermissions()
            if (HealthConnectPermissions.activity.all { it in granted }) {
                settingsRepository.setHealthConnectEnabled(true)
                healthConnectScheduler.schedulePeriodic()
                healthConnectScheduler.syncNow(backfill = true)
            }
            refreshHealthConnect()
        }
    }

    /** Result of the glucose permission dialog: Health Connect becomes the CGM source only if it was granted. */
    fun onGlucosePermissionResult() {
        viewModelScope.launch {
            val granted = healthConnectManager.grantedPermissions()
            if (HealthConnectPermissions.glucose.all { it in granted }) onCgmSourceSelected(CgmSource.HEALTH_CONNECT)
            refreshHealthConnect()
        }
    }

    fun syncHealthConnectNow(backfill: Boolean = false) = healthConnectScheduler.syncNow(backfill)

    /** Stops importing (data already imported stays); permissions can be withdrawn in Health Connect itself. */
    fun disconnectHealthConnect() {
        viewModelScope.launch {
            settingsRepository.setHealthConnectEnabled(false)
            healthConnectScheduler.cancel()
            refreshHealthConnect()
        }
    }

    fun healthConnectStoreIntent() = healthConnectManager.storeIntent()
    fun healthConnectSettingsIntent() = healthConnectManager.settingsIntent()

    private fun refreshSettings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val batteryIgnored = appContext.isIgnoringBatteryOptimizations()
            val cgmSource = getCgmSource()
            val bolusSource = getBolusSource()
            val range = getGlucoseTargetRange()
            val nightscoutConfig = getNightscoutConfig()
            val aiConfig = getAiConfig()
            refreshHealthConnect()

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

    fun onAiConfigChanged(baseUrl: String, apiKey: String, model: String = _state.value.aiConfig.model) {
        viewModelScope.launch {
            val newConfig = AiConfig(baseUrl, apiKey, model)
            setAiConfig(newConfig)
            _state.value = _state.value.copy(aiConfig = newConfig)
        }
    }

    fun onAiModelChanged(model: String) {
        val c = _state.value.aiConfig
        onAiConfigChanged(c.baseUrl, c.apiKey, model)
    }

    /** Fetches the model list from the configured endpoint (`GET /models`). */
    fun loadAiModels() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingAiModels = true, aiModelsError = null)
            listAiModels()
                .onSuccess { _state.value = _state.value.copy(aiModels = it, isLoadingAiModels = false) }
                .onFailure { _state.value = _state.value.copy(aiModelsError = it.message ?: "Failed", isLoadingAiModels = false) }
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
