package uk.scimone.diafit.core.data.networking.dto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource.AapsTempBasalParser
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.PumpEventEntity
import uk.scimone.diafit.core.domain.model.PumpEventNormalizer
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

    /**
     * Any other treatment (profile switch, temporary target, site / sensor / insulin / battery change, note...) as the
     * same [PumpEventEntity] the live AAPS path stores. Null for boluses, carbs, temp basals and invalidated documents.
     */
    fun toPumpEvent(t: JsonObject, userId: Int = 1, now: Long = System.currentTimeMillis()): PumpEventEntity? {
        val eventType = t.str("eventType") ?: "Unknown"
        if ((t.num("insulin") ?: 0.0) > 0.0 || (t.num("carbs") ?: 0.0) > 0.0) return null
        if (eventType.startsWith("Temp Basal", ignoreCase = true)) return null
        if ((t["isValid"] as? JsonPrimitive)?.booleanOrNull == false) return null
        val ts = timestampOf(t) ?: return null
        val duration = (t.num("durationInMilliseconds")?.div(60_000.0)) ?: t.num("duration")
        val event = PumpEventEntity(
            userId = userId, timestampUtc = ts, createdAtUtc = now,
            eventType = eventType,
            notes = t.str("notes"),
            durationMinutes = duration?.takeIf { !it.isNaN() },
            rate = t.num("rate"),
            sourceId = t.str("_id") ?: "$eventType-$ts",
            rawJson = t.toString()
        )
        // AAPS also logs profile changes as Notes carrying the profile: those are profile switches.
        return PumpEventNormalizer.withSource(PumpEventNormalizer.profileNoteToSwitch(event) ?: event, "Nightscout")
    }

    /**
     * A document of Nightscout's `profile` collection as a "Profile Switch" at 100 % with the stored profile attached,
     * so the Profile page and the basal timeline can use it like a switch AAPS made. Null if it holds no usable profile.
     */
    fun profileDocToSwitch(doc: JsonObject, userId: Int = 1, now: Long = System.currentTimeMillis()): PumpEventEntity? {
        val store = doc["store"] as? JsonObject ?: return null
        val name = t(doc.str("defaultProfile"), store.keys.firstOrNull()) ?: return null
        val profile = store[name] as? JsonObject ?: return null
        val ts = (doc.str("startDate") ?: doc.str("created_at"))
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?.takeIf { it > 0 } ?: return null
        val raw = buildJsonObject {
            put("eventType", "Profile Switch")
            put("profile", name)
            put("profileJson", profile.toString())
            put("percentage", 100)
            put("duration", 0)
            put("timeshift", 0)
            put("created_at", Instant.ofEpochMilli(ts).toString())
        }
        return PumpEventEntity(
            userId = userId, timestampUtc = ts, createdAtUtc = now, eventType = "Profile Switch",
            durationMinutes = 0.0, sourceId = "ns-profile-${doc.str("_id") ?: ts}", rawJson = raw.toString()
        ).let { PumpEventNormalizer.withSource(it, "Nightscout") }
    }

    private fun t(a: String?, b: String?): String? = a?.takeIf { it.isNotEmpty() } ?: b
}
