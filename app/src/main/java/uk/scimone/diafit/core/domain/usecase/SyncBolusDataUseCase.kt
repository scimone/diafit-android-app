package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.repository.syncsource.HealthSyncSource
import uk.scimone.diafit.settings.domain.model.BolusSource
import uk.scimone.diafit.settings.domain.usecase.GetBolusSourceUseCase

class SyncBolusDataUseCase(
    private val getBolusSourceUseCase: GetBolusSourceUseCase,
    private val sources: Map<BolusSource, HealthSyncSource>
) {
    suspend operator fun invoke() {
        val currentSource = getBolusSourceUseCase()
        val actualSource = sources[currentSource]
            ?: error("Unsupported Bolus source: $currentSource")
        actualSource.sync()
    }
}
