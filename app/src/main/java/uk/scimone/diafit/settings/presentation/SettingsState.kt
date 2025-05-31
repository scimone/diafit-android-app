package uk.scimone.diafit.settings.presentation

import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange

data class SettingsState(
    val selectedCgmSource: CgmSource = CgmSource.MOCK,
    val glucoseTargetRange: SettingsGlucoseTargetRange = SettingsGlucoseTargetRange(),
    val isLoading: Boolean = false
)
