package uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource

import uk.scimone.diafit.core.domain.model.DeviceStatus

/**
 * Pump levels from the AAPS `info.nightscout.androidaps.status` broadcast. Seen on a real Omnipod setup (2026-10-08):
 * `pumpReservoir` (U), `phoneBattery` (%), `pumpStatus` (text); no pump-battery extra for a pod. Other likely
 * spellings are tried too (numbers or numeric strings, "85%" / "112.5 U" tolerated); the receiver
 * logs every extra's name so the real ones can be read off with `adb logcat AapsStatus:I`.
 */
object AapsDeviceStatusParser {
    private val RESERVOIR_KEYS = listOf("pumpReservoir", "reservoir", "pumpReservoirLevel", "reservoirLevel")
    private val BATTERY_KEYS = listOf("pumpBattery", "pumpBatteryLevel", "pumpBatteryPercent", "batteryLevel")
    private val STATUS_KEYS = listOf("pumpStatus", "pump")

    fun parse(extras: Map<String, Any?>, nowUtc: Long): DeviceStatus? {
        val reservoir = number(extras, RESERVOIR_KEYS)
        val battery = number(extras, BATTERY_KEYS)
        val status = STATUS_KEYS.firstNotNullOfOrNull { (extras[it] as? String)?.takeIf { s -> s.isNotBlank() && s.length < 120 } }
        val phone = number(extras, listOf("phoneBattery"))
        if (reservoir == null && battery == null && phone == null) return null
        return DeviceStatus(nowUtc, reservoirUnits = reservoir, pumpBatteryPercent = battery, uploaderBatteryPercent = phone, pumpStatus = status)
    }

    private fun number(extras: Map<String, Any?>, keys: List<String>): Double? = keys.firstNotNullOfOrNull { k ->
        when (val v = extras[k]) {
            is Number -> v.toDouble()
            is String -> Regex("-?\\d+(\\.\\d+)?").find(v.replace(',', '.'))?.value?.toDoubleOrNull()
            else -> null
        }
    }?.takeIf { !it.isNaN() && it >= 0 }
}
