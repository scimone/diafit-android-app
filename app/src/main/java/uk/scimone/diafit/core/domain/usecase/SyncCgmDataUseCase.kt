package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.repository.syncsource.HealthSyncSource
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase

class SyncCgmDataUseCase(
    private val getCgmSourceUseCase: GetCgmSourceUseCase,
    private val sources: Map<CgmSource, HealthSyncSource>
) {
    suspend operator fun invoke() {
        val currentSource = getCgmSourceUseCase()
        val actualSource = sources[currentSource]
            ?: error("Unsupported CGM source: $currentSource")
        actualSource.sync()
    }
}
