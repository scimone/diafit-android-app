package uk.scimone.diafit.core.domain.repository.syncsource

import android.content.Intent
import uk.scimone.diafit.core.domain.model.CgmEntity

interface CgmSyncSource {
    suspend fun sync()
}

interface IntentCgmSyncSource : CgmSyncSource {
    fun handleIntent(intent: Intent): CgmEntity?
}