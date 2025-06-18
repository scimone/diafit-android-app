package uk.scimone.diafit.core.domain.repository.syncsource

import android.content.Intent

interface HealthSyncSource {
    suspend fun sync()
}

interface IntentHealthSyncSource : HealthSyncSource {
    fun handleIntent(intent: Intent): Any?
}