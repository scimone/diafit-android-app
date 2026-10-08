package uk.scimone.diafit.core.domain.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/** The latest levels Nightscout knows from `devicestatus`; every field is null when the uploader doesn't report it. */
data class DeviceStatus(
    val timestampUtc: Long,
    /** Units left in the pump reservoir / pod. */
    val reservoirUnits: Double? = null,
    val pumpBatteryPercent: Double? = null,
    val pumpBatteryVolt: Double? = null,
    val uploaderBatteryPercent: Double? = null,
    val pumpStatus: String? = null
) {
    val hasPump: Boolean get() = reservoirUnits != null || pumpBatteryPercent != null || pumpBatteryVolt != null
}

object NightscoutDeviceStatusParser {
    /** Merges the newest documents: for each field the most recent document that carries it wins. */
    fun parse(docs: List<JsonObject>): DeviceStatus? {
        val parsed = docs.mapNotNull { parseOne(it) }.sortedByDescending { it.timestampUtc }
        val newest = parsed.firstOrNull() ?: return null
        return DeviceStatus(
            timestampUtc = newest.timestampUtc,
            reservoirUnits = parsed.firstNotNullOfOrNull { it.reservoirUnits },
            pumpBatteryPercent = parsed.firstNotNullOfOrNull { it.pumpBatteryPercent },
            pumpBatteryVolt = parsed.firstNotNullOfOrNull { it.pumpBatteryVolt },
            uploaderBatteryPercent = parsed.firstNotNullOfOrNull { it.uploaderBatteryPercent },
            pumpStatus = parsed.firstNotNullOfOrNull { it.pumpStatus }
        )
    }

    private fun parseOne(doc: JsonObject): DeviceStatus? {
        val time = (doc["created_at"] as? JsonPrimitive)?.content
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: doc["mills"].num()?.toLong() ?: return null
        val pump = doc["pump"] as? JsonObject
        val battery = pump?.get("battery")
        val uploader = doc["uploader"] as? JsonObject
        return DeviceStatus(
            timestampUtc = time,
            reservoirUnits = pump?.get("reservoir").num(),
            // AAPS/Loop send {"percent": 80}; older uploaders a bare number; some only volt.
            pumpBatteryPercent = (battery as? JsonObject)?.get("percent").num() ?: (battery as? JsonPrimitive).num(),
            pumpBatteryVolt = (battery as? JsonObject)?.get("voltage").num(),
            uploaderBatteryPercent = uploader?.get("battery").num(),
            pumpStatus = ((pump?.get("status") as? JsonObject)?.get("status") as? JsonPrimitive)?.content
        )
    }

    private fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.doubleOrNull
}
