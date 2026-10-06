package uk.scimone.diafit.settings.domain.usecase

import uk.scimone.diafit.settings.domain.model.AiConfig
import uk.scimone.diafit.settings.domain.repository.SettingsRepository

class GetAiConfigUseCase(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): AiConfig = repository.getAiConfig()
}
