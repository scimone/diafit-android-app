package uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource

import android.content.Intent
import uk.scimone.diafit.core.presentation.receivers.Intents
import android.util.Log
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.repository.syncsource.IntentCgmSyncSource

class CgmSyncSourceJuggluco(
) : IntentCgmSyncSource {

    val name: String = "JUGGLUCO"

    companion object {
        const val ACTION = Intents.JUGGLUCO_NEW_CGM  // use Intents constant here
        private const val CGMVALUE = "$ACTION.mgdl"
        private const val RATE = "$ACTION.Rate"
        private const val TIMESTAMP = "$ACTION.Time"
        private const val TAG = "CgmSyncJuggluco"
    }

    override suspend fun sync() {
        // Not applicable for intent sources, leave empty
    }

    override fun handleIntent(intent: Intent): CgmEntity? {
        if (intent.action != ACTION) {
            Log.w(TAG, "Unexpected intent action: ${intent.action}")
            return null
        }

        val cgmValue = intent.getIntExtra(CGMVALUE, 0)
        val rate = intent.getFloatExtra(RATE, 0f)
        val timestamp = intent.getLongExtra(TIMESTAMP, 0L)

        if (timestamp == 0L) {
            Log.w(TAG, "Invalid timestamp in intent")
            return null
        }

        return CgmEntity(
            userId = 1,
            timestamp = timestamp,
            valueMgdl = cgmValue,
            fiveMinuteRateMgdl = rate * 5, // Convert rate to 5-minute value
            device = "Freestyle Libre",
            source = "Juggluco",
        )
    }
}
