package uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource

import uk.scimone.diafit.core.domain.repository.syncsource.CgmSyncSource

class CgmSyncSourceMockData : CgmSyncSource {
    override suspend fun sync() {
        println("Mock sync for Mock Data source")
    }
}