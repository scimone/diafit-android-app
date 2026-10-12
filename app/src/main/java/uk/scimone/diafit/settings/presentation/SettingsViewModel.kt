package uk.scimone.diafit.settings.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.data.backfill.BackfillRunner
import uk.scimone.diafit.core.data.backfill.BackfillStatus
import uk.scimone.diafit.core.domain.model.TimeRange
import uk.scimone.diafit.core.data.networking.NightscoutApi
import uk.scimone.diafit.core.data.networking.NightscoutCheck
import uk.scimone.diafit.core.data.healthconnect.HealthConnectImportSummary
import uk.scimone.diafit.core.data.healthconnect.HealthConnectManager
import uk.scimone.diafit.core.data.healthconnect.HealthConnectPermissions
import uk.scimone.diafit.core.data.healthconnect.HealthConnectScheduler
import uk.scimone.diafit.core.data.healthconnect.HealthConnectSyncer
import uk.scimone.diafit.settings.domain.repository.SettingsRepository
import uk.scimone.diafit.settings.domain.model.AiConnection
import uk.scimone.diafit.settings.domain.model.Connector
import uk.scimone.diafit.settings.domain.model.DataType
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.model.NightscoutConfig
import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange
import uk.scimone.diafit.settings.domain.usecase.GetNightscoutConfigUseCase
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.domain.usecase.ListAiModelsUseCase
import uk.scimone.diafit.settings.domain.usecase.SetNightscoutConfigUseCase
import uk.scimone.diafit.settings.domain.usecase.SetTargetRangeUseCase
import uk.scimone.diafit.settings.isIgnoringBatteryOptimizations

class SettingsViewModel(
    private val getGlucoseTargetRange: GetTargetRangeUseCase,
    private val setGlucoseTargetRange: SetTargetRangeUseCase,
    private val getNightscoutConfig: GetNightscoutConfigUseCase,
    private val setNightscoutConfig: SetNightscoutConfigUseCase,
    private val listAiModels: ListAiModelsUseCase,
    private val foodProducts: uk.scimone.diafit.core.domain.repository.FoodProductRepository,
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val healthConnectManager: HealthConnectManager,
    private val healthConnectSyncer: HealthConnectSyncer,
    private val healthConnectScheduler: HealthConnectScheduler,
    private val nightscoutSyncScheduler: uk.scimone.diafit.core.data.nightscout.NightscoutSyncScheduler,
    private val nightscoutApi: NightscoutApi,
    private val backfillRunner: BackfillRunner
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    /** The CGM connector to run (null: stop CGM sync); emitted whenever the CGM selection may have changed. */
    private val _restartCgmServiceEvent = MutableSharedFlow<CgmSource?>()
    val restartCgmServiceEvent = _restartCgmServiceEvent.asSharedFlow()

    init {
        refreshSettings()
        viewModelScope.launch {
            backfillRunner.status.collect { s ->
                _state.value = _state.value.copy(backfill = s)
                // New data landed: Home / History re-read it.
                if ((s is BackfillStatus.Done && !s.nothingMissing) || (s is BackfillStatus.DoneAll && !s.nothingMissing)) SettingsChangeBus.notifyChange()
            }
        }
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
                        historyGranted = HealthConnectPermissions.HISTORY in granted,
                        lastSync = lastSync,
                        summary = HealthConnectImportSummary.decode(settingsRepository.getHealthConnectSummary())
                    )
                )
            }
        }
    }

    /**
     * Result of Health Connect's permission dialog (activity and glucose are asked for together). Connects
     * Health Connect when at least one kind of data was allowed; the data types it can now deliver are
     * the ones the user then picks it for.
     */
    fun onHealthConnectPermissionsResult() {
        viewModelScope.launch {
            val granted = healthConnectManager.grantedPermissions()
            val activity = HealthConnectPermissions.activity.all { it in granted }
            val glucose = HealthConnectPermissions.glucose.all { it in granted }
            if (activity || glucose) {
                settingsRepository.setConnectorEnabled(Connector.HEALTH_CONNECT, true)
                // A type whose permission was refused can't be fed by it.
                if (!glucose && settingsRepository.getSelection(DataType.CGM) == Connector.HEALTH_CONNECT) {
                    settingsRepository.setSelection(DataType.CGM, null)
                }
                if (!activity) DataType.ACTIVITY.filter { settingsRepository.getSelection(it) == Connector.HEALTH_CONNECT }
                    .forEach { settingsRepository.setSelection(it, null) }
                if (activity) {
                    healthConnectScheduler.schedulePeriodic()
                    healthConnectScheduler.syncNow(backfill = true)
                }
                connectorsChanged(cgmMayHaveChanged = true)
            }
            refreshHealthConnect()
        }
    }

    fun syncHealthConnectNow(backfill: Boolean = false) = healthConnectScheduler.syncNow(backfill)

    /** Stops importing (data already imported stays); permissions can be withdrawn in Health Connect itself. */
    fun disconnectHealthConnect() {
        viewModelScope.launch {
            settingsRepository.setConnectorEnabled(Connector.HEALTH_CONNECT, false)
            healthConnectScheduler.cancel()
            refreshHealthConnect()
            connectorsChanged(cgmMayHaveChanged = true)
        }
    }

    fun healthConnectStoreIntent() = healthConnectManager.storeIntent()
    fun healthConnectSettingsIntent() = healthConnectManager.settingsIntent()

    private fun refreshSettings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            val batteryIgnored = appContext.isIgnoringBatteryOptimizations()
            loadConnectors()
            val range = getGlucoseTargetRange()
            val nightscoutConfig = getNightscoutConfig()
            refreshHealthConnect()

            _state.value = _state.value.copy(
                glucoseTargetRange = range,
                basalStyle = settingsRepository.getBasalStyle(),
                nightscoutConfig = nightscoutConfig,
                aiConnections = settingsRepository.getAiConnections(),
                mealEstimation = settingsRepository.getMealEstimationSettings(),
                usdaKey = settingsRepository.getUsdaApiKey(),
                aiEnabled = settingsRepository.isAiEnabled(),
                userName = settingsRepository.getUserName(),
                isBatteryOptimizationIgnored = batteryIgnored,
                isLoading = false
            )
        }
    }

    /** Reads which connectors are connected and which one feeds each data type. */
    private suspend fun loadConnectors() {
        val enabled = settingsRepository.getEnabledConnectors()
        val selections = DataType.values().associateWith { settingsRepository.getSelection(it) }
        _state.value = _state.value.copy(enabledConnectors = enabled, selections = selections)
    }

    private fun connectorsChanged(cgmMayHaveChanged: Boolean) {
        viewModelScope.launch {
            loadConnectors()
            if (cgmMayHaveChanged) _restartCgmServiceEvent.emit(settingsRepository.getCgmSource())
            SettingsChangeBus.notifyChange()
        }
    }

    /** Connects or disconnects a connector that needs no permission dialog (Nightscout, AAPS, xDrip+, Juggluco). */
    fun onConnectorToggled(connector: Connector, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setConnectorEnabled(connector, enabled)
            if (enabled && connector == Connector.NIGHTSCOUT) nightscoutSyncScheduler.syncNow()
            connectorsChanged(cgmMayHaveChanged = DataType.CGM in connector.provides)
        }
    }

    /** Which connector feeds [type]; null turns that data off. */
    fun onSelectionChanged(type: DataType, connector: Connector?) {
        viewModelScope.launch {
            settingsRepository.setSelection(type, connector)
            loadConnectors()
            if (type == DataType.CGM) _restartCgmServiceEvent.emit(settingsRepository.getCgmSource())
            // Newly wanted Health Connect activity data: fetch it right away.
            if (connector == Connector.HEALTH_CONNECT && type.isActivity) healthConnectScheduler.syncNow()
            if (connector == Connector.NIGHTSCOUT && type != DataType.CGM) nightscoutSyncScheduler.syncNow()
            SettingsChangeBus.notifyChange()
        }
    }

    /** Fetches [type] for [range] from [connector], skipping what is already there. */
    fun startBackfill(type: DataType, connector: Connector, range: TimeRange) = backfillRunner.start(type, connector, range)
    fun startBackfillAll(connector: Connector, types: List<DataType>, range: TimeRange) = backfillRunner.startAll(connector, types, range)
    fun dismissBackfill() = backfillRunner.dismiss()

    /** The parts of [range] that would be fetched (the rest already has data). */
    suspend fun missingRanges(type: DataType, range: TimeRange): List<TimeRange> = backfillRunner.missing(type, range)

    /** Verifies the saved Nightscout address and credentials. */
    fun testNightscout() {
        viewModelScope.launch {
            _state.value = _state.value.copy(nightscoutCheck = NightscoutCheckState.Checking)
            val result = nightscoutApi.testConnection()
            _state.value = _state.value.copy(
                nightscoutCheck = when (result) {
                    NightscoutCheck.Ok -> NightscoutCheckState.Ok
                    is NightscoutCheck.Failed -> NightscoutCheckState.Failed(result.message)
                }
            )
        }
    }

    fun onBasalStyleChanged(style: uk.scimone.diafit.settings.domain.model.BasalStyle) {
        viewModelScope.launch {
            settingsRepository.setBasalStyle(style)
            _state.value = _state.value.copy(basalStyle = style)
            SettingsChangeBus.notifyChange()
        }
    }

    fun onGlucoseTargetRangeChanged(lower: Int, upper: Int, veryLow: Int, veryHigh: Int) {
        viewModelScope.launch {
            val newRange = SettingsGlucoseTargetRange(lower, upper, veryLow, veryHigh)
            setGlucoseTargetRange(newRange)
            _state.value = _state.value.copy(glucoseTargetRange = newRange)
            SettingsChangeBus.notifyChange()
        }
    }

    fun onNightscoutConfigChanged(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            val newConfig = NightscoutConfig(baseUrl, apiKey)
            setNightscoutConfig(newConfig)
            _state.value = _state.value.copy(nightscoutConfig = newConfig, nightscoutCheck = NightscoutCheckState.Idle)
            SettingsChangeBus.notifyChange()
        }
    }

    /** Adds a connection, or replaces the one with the same id. The first one also becomes the meal estimation's. */
    fun saveAiConnection(connection: AiConnection) {
        val list = _state.value.aiConnections.let { old ->
            if (old.any { it.id == connection.id }) old.map { if (it.id == connection.id) connection else it } else old + connection
        }
        _state.update { it.copy(aiConnections = list, aiChecks = it.aiChecks - connection.id) }
        viewModelScope.launch { settingsRepository.setAiConnections(list) }
        if (_state.value.mealEstimation.connectionId == null) onMealConnectionChanged(connection.id)
    }

    fun deleteAiConnection(id: String) {
        val list = _state.value.aiConnections.filterNot { it.id == id }
        _state.update { it.copy(aiConnections = list, aiChecks = it.aiChecks - id) }
        viewModelScope.launch { settingsRepository.setAiConnections(list) }
        if (_state.value.mealEstimation.connectionId == id) onMealConnectionChanged(list.firstOrNull()?.id)
    }

    /** Asks the endpoint for its models; a connection that answers works. */
    fun testAiConnection(id: String) {
        val connection = _state.value.aiConnections.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(aiChecks = it.aiChecks + (id to NightscoutCheckState.Checking)) }
        viewModelScope.launch {
            listAiModels(connection)
                .onSuccess { _state.update { s -> s.copy(aiChecks = s.aiChecks + (id to NightscoutCheckState.Ok)) } }
                .onFailure { e -> _state.update { s -> s.copy(aiChecks = s.aiChecks + (id to NightscoutCheckState.Failed(e.message ?: "Failed"))) } }
        }
    }

    private fun updateMealEstimation(change: (uk.scimone.diafit.settings.domain.model.MealEstimationSettings) -> uk.scimone.diafit.settings.domain.model.MealEstimationSettings) {
        _state.update { it.copy(mealEstimation = change(it.mealEstimation)) }
        viewModelScope.launch { settingsRepository.setMealEstimationSettings(_state.value.mealEstimation) }
    }

    fun onMealConnectionChanged(id: String?) {
        updateMealEstimation { it.copy(connectionId = id) }
        _state.update { it.copy(aiModels = emptyList(), aiModelsError = null) }
    }

    fun onMealModelChanged(model: String) = updateMealEstimation { it.copy(model = model) }

    /** Blank restores the built-in prompt. */
    fun onMealPromptChanged(prompt: String) = updateMealEstimation { it.copy(prompt = prompt) }

    /** Loads the model list of the connection meal photos go to. */
    fun loadMealModels() {
        val connection = _state.value.mealConnection ?: return
        _state.update { it.copy(isLoadingAiModels = true, aiModelsError = null) }
        viewModelScope.launch {
            listAiModels(connection)
                .onSuccess { models -> _state.update { it.copy(aiModels = models, isLoadingAiModels = false) } }
                .onFailure { e -> _state.update { it.copy(aiModelsError = e.message ?: "Failed", isLoadingAiModels = false) } }
        }
    }

    fun onAiEnabledChanged(enabled: Boolean) {
        _state.value = _state.value.copy(aiEnabled = enabled)
        viewModelScope.launch { settingsRepository.setAiEnabled(enabled) }
    }

    fun onUserNameChanged(name: String) {
        _state.value = _state.value.copy(userName = name.trim())
        viewModelScope.launch { settingsRepository.setUserName(name) }
    }

    fun onUsdaKeyChanged(key: String) {
        _state.update { it.copy(usdaKey = key, usdaCheck = NightscoutCheckState.Idle) }
        viewModelScope.launch { settingsRepository.setUsdaApiKey(_state.value.usdaKey) }
    }

    /** Runs a small search with the entered key. */
    fun testUsdaKey() {
        val key = _state.value.usdaKey
        _state.update { it.copy(usdaCheck = NightscoutCheckState.Checking) }
        viewModelScope.launch {
            settingsRepository.setUsdaApiKey(key)
            foodProducts.testUsdaKey(key)
                .onSuccess { _state.update { it.copy(usdaCheck = NightscoutCheckState.Ok) } }
                .onFailure { e -> _state.update { it.copy(usdaCheck = NightscoutCheckState.Failed(e.message ?: "Failed")) } }
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
