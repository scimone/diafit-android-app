package uk.scimone.diafit.core.domain.repository.syncsource

import android.content.Intent

interface CgmSyncSource {
    suspend fun sync()
}

interface IntentCgmSyncSource : CgmSyncSource {
    fun handleIntent(intent: Intent): Boolean
}