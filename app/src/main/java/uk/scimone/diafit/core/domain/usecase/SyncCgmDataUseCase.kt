package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.repository.CgmSyncSource

class SyncCgmDataUseCase(
    private val syncSources: List<CgmSyncSource>
) {
    suspend operator fun invoke() {
        syncSources.forEach { it.sync() }
    }
}
