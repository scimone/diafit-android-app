package uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource

import android.content.Intent
import android.util.Log
import org.json.JSONArray
import uk.scimone.diafit.core.presentation.receivers.Intents
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.repository.syncsource.IntentHealthSyncSource

class BolusSyncSourceAaps : IntentHealthSyncSource {

    val name: String = "AAPS"

    companion object {
        const val ACTION = Intents.NSCLIENT_NEW_FOOD
        private const val TAG = "BolusSyncAaps"
    }

    override suspend fun sync() {
        // Not applicable for intent sources, leave empty
    }

    override fun handleIntent(intent: Intent): BolusEntity? {
        if (intent.action != ACTION) {
            Log.w(TAG, "Unexpected intent action: ${intent.action}")
            return null
        }

        Log.i(TAG, "Received NEW_FOOD intent for bolus sync")

        val treatmentsJson = intent.extras?.getString("treatments", "")

        if (treatmentsJson.isNullOrEmpty()) {
            Log.e(TAG, "Empty treatments JSON for action: $ACTION")
            return null
        }

        Log.d(TAG, "Treatments JSON: $treatmentsJson")

        return try {
            val jsonArray = JSONArray(treatmentsJson)
            if (jsonArray.length() == 0) {
                Log.w(TAG, "Empty treatments list")
                return null
            }

            val treatment = jsonArray.getJSONObject(0)
            if (!treatment.has("insulin")) {
                Log.e(TAG, "Treatment is not a bolus")
                return null
            }

            val insulin = treatment.optDouble("insulin", 0.0)
            val timestamp = treatment.optLong("date", 0L)
            val sourceId = treatment.optString("_id", null)
            val eventType = treatment.optString("eventType", "Bolus")
            val isSmb = treatment.optBoolean("isSMB", false)
            val pumpType = treatment.optString("pumpType", "AAPS")
            val pumpSerial = treatment.optString("pumpSerial", "AAPS-12345")
            val pumpId = treatment.optLong("pumpId", 12345L)

            if (timestamp == 0L) {
                Log.e(TAG, "Invalid timestamp in treatment JSON")
                return null
            }

            BolusEntity(
                userId = 1,
                timestampUtc = timestamp,
                createdAtUtc = System.currentTimeMillis(),
                updatedAtUtc = System.currentTimeMillis(),
                value = insulin.toFloat(),
                eventType = eventType,
                isSmb = isSmb,
                pumpType = pumpType,
                pumpSerial = pumpSerial,
                pumpId = pumpId,
                sourceId = sourceId
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse treatments JSON", e)
            null
        }
    }
}
