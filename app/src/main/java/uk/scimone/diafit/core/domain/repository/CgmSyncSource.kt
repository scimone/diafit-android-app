package uk.scimone.diafit.core.domain.repository

interface CgmSyncSource {
    suspend fun sync()
}