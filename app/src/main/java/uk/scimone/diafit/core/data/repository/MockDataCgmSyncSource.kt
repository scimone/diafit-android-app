package uk.scimone.diafit.core.data.repository

import uk.scimone.diafit.core.domain.repository.CgmSyncSource

class MockDataCgmSyncSource : CgmSyncSource {
    override suspend fun sync() {
        println("Mock sync for Mock Data source")
    }
}