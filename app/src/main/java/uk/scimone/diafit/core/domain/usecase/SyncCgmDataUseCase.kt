package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.repository.syncsource.CgmSyncSource
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase

class SyncCgmDataUseCase(
    private val getCgmSourceUseCase: GetCgmSourceUseCase,
    private val nightscoutSource: CgmSyncSource,
    private val xdripSource: CgmSyncSource,
    private val jugglucoSource: CgmSyncSource,
    private val mockSource: CgmSyncSource
) {
    suspend operator fun invoke() {
        val currentSource = getCgmSourceUseCase()

        val actualSource = when (currentSource) {
            CgmSource.NIGHTSCOUT -> nightscoutSource
            CgmSource.XDRIP -> xdripSource
            CgmSource.JUGGLUCO -> jugglucoSource
            CgmSource.MOCK -> mockSource
        }

        actualSource.sync()
    }
}
