package uk.scimone.diafit.settings.presentation

import uk.scimone.diafit.core.data.healthconnect.HealthConnectAvailability
import uk.scimone.diafit.core.data.healthconnect.HealthConnectSyncStatus
import uk.scimone.diafit.settings.domain.model.AiConfig
import uk.scimone.diafit.settings.domain.model.BolusSource
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.model.NightscoutConfig
import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange

data class SettingsState(
    val selectedCgmSource: CgmSource = CgmSource.JUGGLUCO,
    val selectedBolusSource: BolusSource = BolusSource.AAPS,
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
    val lastSync: Long? = null,
    val sync: HealthConnectSyncStatus = HealthConnectSyncStatus.Idle
) {
    /** Importing activity data: switched on and Health Connect still lets us read it. */
    val connected: Boolean get() = enabled && activityGranted
}