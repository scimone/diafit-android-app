package uk.scimone.diafit.settings.presentation

import uk.scimone.diafit.core.presentation.UiText
import uk.scimone.diafit.core.data.backfill.BackfillStatus
import uk.scimone.diafit.core.data.healthconnect.HealthConnectAvailability
import uk.scimone.diafit.core.data.healthconnect.HealthConnectImportSummary
import uk.scimone.diafit.core.data.healthconnect.HealthConnectSyncStatus
import uk.scimone.diafit.settings.domain.model.AiConnection
import uk.scimone.diafit.settings.domain.model.MealEstimationSettings
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
    val aiConnections: List<AiConnection> = emptyList(),
    val mealEstimation: MealEstimationSettings = MealEstimationSettings(),
    /** Result of the last connection test per connection id. */
    val aiChecks: Map<String, NightscoutCheckState> = emptyMap(),
    val aiModels: List<String> = emptyList(),
    val isLoadingAiModels: Boolean = false,
    val aiModelsError: UiText? = null,
    val aiEnabled: Boolean = true,
    val userName: String = "",
    val usdaKey: String = "",
    val usdaCheck: NightscoutCheckState = NightscoutCheckState.Idle,
    val isBatteryOptimizationIgnored: Boolean = false,
    val healthConnect: HealthConnectUiState = HealthConnectUiState(),
    val isLoading: Boolean = false
) {
    /** The connection meal photos go to: the chosen one, else the first. */
    val mealConnection: AiConnection? get() = aiConnections.firstOrNull { it.id == mealEstimation.connectionId } ?: aiConnections.firstOrNull()
}

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
    data class Failed(val message: UiText) : NightscoutCheckState
}
