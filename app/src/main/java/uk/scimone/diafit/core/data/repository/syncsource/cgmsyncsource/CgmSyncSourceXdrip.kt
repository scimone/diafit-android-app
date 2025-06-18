package uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource

import android.content.Intent
import android.util.Log
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.repository.syncsource.IntentHealthSyncSource
import uk.scimone.diafit.core.presentation.receivers.Intents

class CgmSyncSourceXdrip : IntentHealthSyncSource {

    companion object {
        const val ACTION = Intents.XDRIP_NEW_CGM
        private const val EXTRAS_BASE = "com.eveningoutpost.dexdrip.Extras"
        private const val CGMVALUE = "$EXTRAS_BASE.BgEstimate"
        private const val RATE = "$EXTRAS_BASE.BgSlope"
        private const val TIMESTAMP = "$EXTRAS_BASE.Time"
        private const val DIRECTION = "$EXTRAS_BASE.BgSlopeName"
        private const val DEVICE = "$EXTRAS_BASE.SourceInfo"
        private const val TAG = "CgmSyncXdrip"
    }

    override suspend fun sync() {
        // No-op for intent-based sources
    }

    override fun handleIntent(intent: Intent): CgmEntity? {
        if (intent.action != ACTION) {
            Log.w(TAG, "Unexpected intent action: ${intent.action}")
            return null
        }

        // Log all extras for debugging
        intent.extras?.keySet()?.forEach { key ->
            val value = intent.extras?.get(key)
            Log.d(TAG, "Intent Extra - Key: $key, Value: $value")
        } ?: Log.d(TAG, "Intent has no extras")

        val cgmValueFloat = intent.getFloatExtra(CGMVALUE, 0f)
        val rateFloat = intent.getFloatExtra(RATE, 0f)
        val timestamp = intent.getLongExtra(TIMESTAMP, 0L)

        if (timestamp == 0L || cgmValueFloat == 0f) {
            Log.w(TAG, "Invalid value in intent")
            return null
        }

        val direction = intent.getStringExtra(DIRECTION) ?: "Unknown"
        val device = intent.getStringExtra(DEVICE) ?: "Unknown"

        return CgmEntity(
            userId = 1,
            timestamp = timestamp,
            valueMgdl = cgmValueFloat.toInt(),
            fiveMinuteRateMgdl = rateFloat,
            device = device,
            direction = direction,
            source = "xDrip"
        )
    }
}
