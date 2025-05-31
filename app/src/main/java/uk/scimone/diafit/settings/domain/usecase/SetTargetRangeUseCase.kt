package uk.scimone.diafit.settings.domain.usecase

import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange
import uk.scimone.diafit.settings.domain.repository.SettingsRepository

class SetTargetRangeUseCase(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(range: SettingsGlucoseTargetRange) =
        settingsRepository.setTargetRange(range)
}