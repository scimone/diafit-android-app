package uk.scimone.diafit.core.domain.repository.syncsource

import android.content.Intent

interface CgmSyncSource {
    suspend fun sync()
}

interface IntentCgmSyncSource {
    fun handleIntent(intent: Intent): Boolean
    suspend fun sync()
}
