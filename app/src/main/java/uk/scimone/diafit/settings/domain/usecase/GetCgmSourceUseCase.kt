package uk.scimone.diafit.settings.domain.usecase

import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.repository.SettingsRepository

class GetCgmSourceUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(): CgmSource = repository.getCgmSource()
}
