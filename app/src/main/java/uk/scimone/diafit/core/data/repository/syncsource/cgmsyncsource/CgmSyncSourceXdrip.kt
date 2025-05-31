package uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource

import android.content.Intent
import uk.scimone.diafit.core.presentation.receivers.Intents
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.repository.syncsource.IntentCgmSyncSource
import uk.scimone.diafit.core.domain.usecase.InsertCgmUseCase

class CgmSyncSourceXdrip(
    private val insertCgmUseCase: InsertCgmUseCase
) : IntentCgmSyncSource {

    val name: String = "XDRIP"

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
        // Not applicable for intent sources, or leave empty
    }

    override fun handleIntent(intent: Intent): Boolean {
        if (intent.action != ACTION) {
            Log.w(TAG, "Unexpected intent action: ${intent.action}")
            return false
        }

        // TODO("Query '$ACTION.Display.Units' to get the unit and convert to mgdl before inserting")

        // Log all extras for debugging
        val extras = intent.extras
        if (extras != null) {
            val keys = extras.keySet()
            for (key in keys) {
                val value = extras.get(key)
                Log.d(TAG, "Intent Extra - Key: $key, Value: $value")
            }
        } else {
            Log.d(TAG, "Intent has no extras")
        }

        val cgmValueDouble = intent.getDoubleExtra(CGMVALUE, 0.0)
        val cgmValue = cgmValueDouble.toFloat().toInt()

        val rateDouble = intent.getDoubleExtra(RATE, 0.0)
        val rate = rateDouble.toFloat()

        val timestamp = intent.getLongExtra(TIMESTAMP, 0L)
        val direction = intent.getStringExtra(DIRECTION) ?: "Unknown"
        val device = intent.getStringExtra(DEVICE) ?: "Unknown"


        if (timestamp == 0L) {
            Log.w(TAG, "Invalid timestamp in intent")
            return false
        }

        val cgmEntity = CgmEntity(
            userId = 1,
            timestamp = timestamp,
            valueMgdl = cgmValue,
            fiveMinuteRateMgdl = rate,
            device = device,
            direction = direction,
            source = "xDrip",
        )

        CoroutineScope(Dispatchers.IO).launch {
            insertCgmUseCase(cgmEntity)
            Log.d(TAG, "Inserted CGM value: $cgmValue")
        }

        return true
    }
}
