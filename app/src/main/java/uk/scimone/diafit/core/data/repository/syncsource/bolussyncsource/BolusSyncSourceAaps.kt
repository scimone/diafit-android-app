package uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource

import android.content.Intent
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
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

    /** Returns every bolus in the batch (an empty list when there is none), not just the first entry. */
    override fun handleIntent(intent: Intent): List<BolusEntity> {
        if (intent.action != ACTION) {
            Log.w(TAG, "Unexpected intent action: ${intent.action}")
            return emptyList()
        }

        Log.i(TAG, "Received NEW_FOOD intent for bolus sync")

        val treatmentsJson = intent.extras?.getString("treatments", "")

        if (treatmentsJson.isNullOrEmpty()) {
            Log.e(TAG, "Empty treatments JSON for action: $ACTION")
            return emptyList()
        }

        Log.d(TAG, "Treatments JSON: $treatmentsJson")

        return try {
            val jsonArray = JSONArray(treatmentsJson)
            (0 until jsonArray.length()).mapNotNull { i ->
                parseTreatment(jsonArray.getJSONObject(i))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse treatments JSON", e)
            emptyList()
        }
    }

    private fun parseTreatment(treatment: JSONObject): BolusEntity? {
        val insulin = treatment.optDouble("insulin", 0.0)
        if (insulin <= 0.0) {
            Log.d(TAG, "Skipping non-bolus treatment: ${treatment.optString("eventType")}")
            return null
        }

        val timestamp = treatment.optLong("date", 0L)
        if (timestamp == 0L) {
            Log.e(TAG, "Invalid timestamp in treatment JSON")
            return null
        }

        val now = System.currentTimeMillis()
        return BolusEntity(
            userId = 1,
            timestampUtc = timestamp,
            createdAtUtc = now,
            updatedAtUtc = now,
            value = insulin.toFloat(),
            eventType = treatment.optString("eventType", "Bolus"),
            isSmb = treatment.optBoolean("isSMB", false),
            pumpType = treatment.optString("pumpType", "AAPS"),
            pumpSerial = treatment.optString("pumpSerial", "AAPS-12345"),
            pumpId = treatment.optLong("pumpId", 12345L),
            sourceId = treatment.optString("_id", "").ifEmpty { null }
        )
    }
}
