package uk.scimone.diafit.settings.presentation

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
    val isLoading: Boolean = false
)