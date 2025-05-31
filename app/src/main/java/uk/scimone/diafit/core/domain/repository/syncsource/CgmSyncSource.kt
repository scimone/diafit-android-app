package uk.scimone.diafit.core.domain.repository.syncsource

interface CgmSyncSource {
    suspend fun sync()
}