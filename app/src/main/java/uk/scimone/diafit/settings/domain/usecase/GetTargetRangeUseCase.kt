package uk.scimone.diafit.settings.domain.usecase

import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange
import uk.scimone.diafit.settings.domain.repository.SettingsRepository

class GetTargetRangeUseCase(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(): SettingsGlucoseTargetRange =
        settingsRepository.getTargetRange()
}
