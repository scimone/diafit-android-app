package uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource

import android.content.Intent
import android.util.Log
import org.json.JSONArray
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity

/**
 * Extracts carb entries from the same AAPS/NSClient `treatments` JSON that carries boluses. Unlike
 * [BolusSyncSourceAaps] this walks the whole array. These meals have no photo (`imageId` is empty);
 * the AI/photo fields stay null and the impact type defaults to MEDIUM (4h absorption).
 */
object AapsCarbParser {
    private const val TAG = "AapsCarbParser"

    fun parse(intent: Intent, userId: Int = 1): List<MealEntity> {
        val json = intent.extras?.getString("treatments", "")
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val t = array.getJSONObject(i)
                val carbs = t.optDouble("carbs", 0.0)
                val timestamp = t.optLong("date", 0L)
                if (carbs <= 0.0 || timestamp == 0L) return@mapNotNull null
                val sourceId = t.optString("_id", "").ifEmpty { "aaps-carbs-$timestamp" }
                MealEntity(
                    userId = userId,
                    description = "Carbs (AAPS)",
                    createdAtUtc = System.currentTimeMillis(),
                    mealTimeUtc = timestamp,
                    carbohydrates = Math.round(carbs).toInt(),
                    calories = null,
                    proteins = null,
                    fats = null,
                    impactType = ImpactType.MEDIUM,
                    imageId = "",
                    recommendation = null,
                    reasoning = null,
                    sourceId = sourceId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse treatments JSON", e)
            emptyList()
        }
    }
}
