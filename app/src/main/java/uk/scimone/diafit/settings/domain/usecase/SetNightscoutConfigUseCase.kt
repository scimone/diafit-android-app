package uk.scimone.diafit.settings.domain.usecase

import uk.scimone.diafit.settings.domain.model.NightscoutConfig
import uk.scimone.diafit.settings.domain.repository.SettingsRepository

class SetNightscoutConfigUseCase(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(config: NightscoutConfig) = repository.setNightscoutConfig(config)
}
