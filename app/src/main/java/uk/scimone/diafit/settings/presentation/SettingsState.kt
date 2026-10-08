package uk.scimone.diafit.settings.presentation

import uk.scimone.diafit.core.data.backfill.BackfillStatus
import uk.scimone.diafit.core.data.healthconnect.HealthConnectAvailability
import uk.scimone.diafit.core.data.healthconnect.HealthConnectImportSummary
import uk.scimone.diafit.core.data.healthconnect.HealthConnectSyncStatus
import uk.scimone.diafit.settings.domain.model.AiConfig
import uk.scimone.diafit.settings.domain.model.Connector
import uk.scimone.diafit.settings.domain.model.DataType
import uk.scimone.diafit.settings.domain.model.NightscoutConfig
import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange

data class SettingsState(
    /** Connected connectors (any number) and the one feeding each data type (null = off). */
    val enabledConnectors: Set<Connector> = emptySet(),
    val selections: Map<DataType, Connector?> = emptyMap(),
    val nightscoutCheck: NightscoutCheckState = NightscoutCheckState.Idle,
    val backfill: BackfillStatus = BackfillStatus.Idle,
    val basalStyle: uk.scimone.diafit.settings.domain.model.BasalStyle = uk.scimone.diafit.settings.domain.model.BasalStyle.RATE,
    val glucoseTargetRange: SettingsGlucoseTargetRange = SettingsGlucoseTargetRange(),
    val nightscoutConfig: NightscoutConfig = NightscoutConfig(baseUrl = "", apiKey = ""),
    val aiConfig: AiConfig = AiConfig(baseUrl = "", apiKey = ""),
    val aiModels: List<String> = emptyList(),
    val isLoadingAiModels: Boolean = false,
    val aiModelsError: String? = null,
    val isBatteryOptimizationIgnored: Boolean = false,
    val healthConnect: HealthConnectUiState = HealthConnectUiState(),
    val isLoading: Boolean = false
)

/** The Health Connect card on the Settings screen. */
data class HealthConnectUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
    /** The user has switched the activity import on. */
    val enabled: Boolean = false,
    val activityGranted: Boolean = false,
    val glucoseGranted: Boolean = false,
    val backgroundGranted: Boolean = false,
    /** May read data older than 30 days before the first grant (needed to backfill long ranges). */
    val historyGranted: Boolean = false,
    val lastSync: Long? = null,
    /** What the last import found, per data type. */
    val summary: HealthConnectImportSummary? = null,
    val sync: HealthConnectSyncStatus = HealthConnectSyncStatus.Idle
) {
    /** Importing activity data: switched on and Health Connect still lets us read it. */
    val connected: Boolean get() = enabled && activityGranted
}

sealed interface NightscoutCheckState {
    data object Idle : NightscoutCheckState
    data object Checking : NightscoutCheckState
    data object Ok : NightscoutCheckState
    data class Failed(val message: String) : NightscoutCheckState
}
