package uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource

import uk.scimone.diafit.core.domain.repository.syncsource.CgmSyncSource

open class MockCgmSyncSource(private val name: String) : CgmSyncSource {
    override suspend fun sync() {
        println("Mock syncing for source: $name")
    }
}
