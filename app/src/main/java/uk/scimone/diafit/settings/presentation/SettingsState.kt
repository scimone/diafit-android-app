package uk.scimone.diafit.settings.presentation

import uk.scimone.diafit.settings.domain.model.BolusSource
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange

data class SettingsState(
    val selectedCgmSource: CgmSource = CgmSource.JUGGLUCO,
    val selectedBolusSource: BolusSource = BolusSource.AAPS,
    val glucoseTargetRange: SettingsGlucoseTargetRange = SettingsGlucoseTargetRange(),
    val isBatteryOptimizationIgnored: Boolean = false,
    val isLoading: Boolean = false
)