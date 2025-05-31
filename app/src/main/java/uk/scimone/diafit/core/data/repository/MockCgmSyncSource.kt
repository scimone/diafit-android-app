package uk.scimone.diafit.core.data.repository

import uk.scimone.diafit.core.domain.repository.CgmSyncSource

open class MockCgmSyncSource(private val name: String) : CgmSyncSource {
    override suspend fun sync() {
        println("Mock syncing for source: $name")
    }
}
