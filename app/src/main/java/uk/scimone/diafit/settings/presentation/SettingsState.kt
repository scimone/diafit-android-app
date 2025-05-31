package uk.scimone.diafit.settings.presentation

import uk.scimone.diafit.settings.domain.model.CgmSource

data class SettingsState(
    val selectedSource: CgmSource = CgmSource.MOCK,
    val isLoading: Boolean = false
)
