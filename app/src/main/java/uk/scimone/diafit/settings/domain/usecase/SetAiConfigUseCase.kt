package uk.scimone.diafit.settings.domain.usecase

import uk.scimone.diafit.settings.domain.model.AiConfig
import uk.scimone.diafit.settings.domain.repository.SettingsRepository

class SetAiConfigUseCase(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(config: AiConfig) = repository.setAiConfig(config)
}
