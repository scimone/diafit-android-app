package uk.scimone.diafit.settings.domain.usecase

import uk.scimone.diafit.settings.domain.model.BolusSource
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.repository.SettingsRepository

class SetBolusSourceUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(source: BolusSource) = repository.setBolusSource(source)
}
