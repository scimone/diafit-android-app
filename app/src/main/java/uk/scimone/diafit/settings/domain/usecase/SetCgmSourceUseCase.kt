package uk.scimone.diafit.settings.domain.usecase

import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.repository.SettingsRepository

class SetCgmSourceUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(source: CgmSource) = repository.setCgmSource(source)
}
