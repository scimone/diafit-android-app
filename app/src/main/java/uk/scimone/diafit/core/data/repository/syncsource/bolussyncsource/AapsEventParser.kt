package uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource

import android.content.Intent
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import uk.scimone.diafit.core.domain.model.PumpEventEntity
import uk.scimone.diafit.core.domain.model.PumpEventNormalizer
import java.time.Instant

/**
 * Extracts every treatment that is neither a bolus nor carbs (those have their own parsers) from the
 * AAPS/NSClient `treatments` JSON: pod/site/insulin changes, temp basals, bolus wizard, profile switches...
 * Every treatment's eventType is logged at info level so unknown types can be found with logcat.
 */
object AapsEventParser {
    private const val TAG = "AapsEventParser"

    fun parse(intent: Intent, userId: Int = 1): List<PumpEventEntity> {
        val json = intent.extras?.getString("treatments", "")
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { parseTreatment(array.getJSONObject(it), userId) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse treatments JSON", e)
            emptyList()
        }
    }

    private fun parseTreatment(t: JSONObject, userId: Int): PumpEventEntity? {
        val eventType = t.optString("eventType", "").ifEmpty { "Unknown" }
        Log.i(TAG, "Treatment eventType='$eventType' insulin=${t.opt("insulin")} carbs=${t.opt("carbs")}")
        if (t.optDouble("insulin", 0.0) > 0.0 || t.optDouble("carbs", 0.0) > 0.0) return null  // bolus / carb paths
        if (!t.optBoolean("isValid", true)) return null

        val timestamp = t.optLong("date", 0L).takeIf { it > 0 }
            ?: runCatching { Instant.parse(t.optString("created_at")).toEpochMilli() }.getOrNull()
            ?: return null

        val duration = when {
            t.has("durationInMilliseconds") -> t.optDouble("durationInMilliseconds") / 60_000.0
            t.has("duration") && !t.isNull("duration") -> t.optDouble("duration")
            else -> null
        }
        val event = PumpEventEntity(
            userId = userId,
            timestampUtc = timestamp,
            createdAtUtc = System.currentTimeMillis(),
            eventType = eventType,
            notes = t.optString("notes", "").ifEmpty { null },
            durationMinutes = duration?.takeIf { !it.isNaN() },
            rate = t.optDouble("rate", Double.NaN).takeIf { !it.isNaN() },
            sourceId = t.optString("_id", "").ifEmpty { "$eventType-$timestamp" },
            rawJson = t.toString()
        )
        return PumpEventNormalizer.withSource(PumpEventNormalizer.profileNoteToSwitch(event) ?: event, "AAPS")
    }
}
