package uk.scimone.diafit.core.data.networking.dto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource.AapsTempBasalParser
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.PumpEventEntity
import java.time.Instant

/**
 * Turns Nightscout `treatments` documents into what the live AAPS broadcasts already produce, so a
 * backfilled row looks like a live one (same `sourceId` = Nightscout `_id`, same defaults).
 */
object NightscoutTreatmentMapper {

    private fun JsonObject.num(key: String): Double? =
        (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content?.takeIf { it.isNotEmpty() }

    /** Epoch ms of the treatment: `date`/`mills` (ms) first, else `created_at`. */
    fun timestampOf(t: JsonObject): Long? =
        (t["date"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
            ?: (t["mills"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
            ?: t.str("created_at")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    fun toBolus(t: JsonObject, userId: Int = 1, now: Long = System.currentTimeMillis()): BolusEntity? {
        val insulin = t.num("insulin") ?: return null
        val ts = timestampOf(t) ?: return null
        if (insulin <= 0.0) return null
        val eventType = t.str("eventType") ?: "Bolus"
        return BolusEntity(
            userId = userId, timestampUtc = ts, createdAtUtc = now, updatedAtUtc = now,
            value = insulin.toFloat(),
            eventType = eventType,
            isSmb = (t["isSMB"] as? JsonPrimitive)?.booleanOrNull == true || eventType.equals("SMB", true),
            pumpType = t.str("pumpType") ?: "AAPS",
            pumpSerial = t.str("pumpSerial") ?: "AAPS-12345",
            pumpId = (t["pumpId"] as? JsonPrimitive)?.longOrNull ?: 12345L,
            sourceId = t.str("_id")
        )
    }

    fun toMeal(t: JsonObject, userId: Int = 1, now: Long = System.currentTimeMillis()): MealEntity? {
        val carbs = t.num("carbs") ?: return null
        val ts = timestampOf(t) ?: return null
        if (carbs <= 0.0) return null
        return MealEntity(
            userId = userId, description = "Carbs", createdAtUtc = now, mealTimeUtc = ts,
            carbohydrates = Math.round(carbs).toInt(),
            calories = null, proteins = null, fats = null,
            impactType = ImpactType.MEDIUM, imageId = "", recommendation = null, reasoning = null,
            sourceId = t.str("_id") ?: "aaps-carbs-$ts"
        )
    }

    /** A temp basal start, or a cancellation (duration 0, stored as rate 0 so it ends the previous one). */
    fun toTempBasal(t: JsonObject, userId: Int = 1, now: Long = System.currentTimeMillis()): PumpEventEntity? {
        val type = t.str("eventType") ?: return null
        if (!type.startsWith("Temp Basal", ignoreCase = true)) return null
        val ts = timestampOf(t) ?: return null
        val duration = t.num("duration") ?: 0.0
        val rate = t.num("absolute") ?: t.num("rate") ?: if (duration <= 0.0) 0.0 else return null
        return PumpEventEntity(
            userId = userId, timestampUtc = ts, createdAtUtc = now,
            eventType = AapsTempBasalParser.EVENT_TYPE,
            durationMinutes = duration, rate = rate,
            sourceId = "ns-temp-basal-${t.str("_id") ?: ts}",
            rawJson = t.toString()
        )
    }
}
