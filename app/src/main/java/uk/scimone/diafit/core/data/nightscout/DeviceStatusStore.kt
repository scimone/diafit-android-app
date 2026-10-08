package uk.scimone.diafit.core.data.nightscout

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.scimone.diafit.core.domain.model.DeviceStatus

/** The latest Nightscout device status, kept in preferences (one small snapshot, not a history). */
class DeviceStatusStore(context: Context) {
    private val prefs = context.getSharedPreferences("device_status", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(read())
    val status: StateFlow<DeviceStatus?> = _status

    fun save(s: DeviceStatus) {
        prefs.edit()
            .putLong("time", s.timestampUtc)
            .putString("reservoir", s.reservoirUnits?.toString())
            .putString("pumpBattery", s.pumpBatteryPercent?.toString())
            .putString("pumpVolt", s.pumpBatteryVolt?.toString())
            .putString("uploaderBattery", s.uploaderBatteryPercent?.toString())
            .putString("pumpStatus", s.pumpStatus)
            .apply()
        _status.value = s
    }

    /** Overlays the fields [s] reports on the stored snapshot (a partial update keeps the other levels). */
    fun merge(s: DeviceStatus) {
        val old = _status.value
        save(
            DeviceStatus(
                timestampUtc = maxOf(s.timestampUtc, old?.timestampUtc ?: 0L),
                reservoirUnits = s.reservoirUnits ?: old?.reservoirUnits,
                pumpBatteryPercent = s.pumpBatteryPercent ?: old?.pumpBatteryPercent,
                pumpBatteryVolt = s.pumpBatteryVolt ?: old?.pumpBatteryVolt,
                uploaderBatteryPercent = s.uploaderBatteryPercent ?: old?.uploaderBatteryPercent,
                pumpStatus = s.pumpStatus ?: old?.pumpStatus
            )
        )
    }

    private fun read(): DeviceStatus? {
        val time = prefs.getLong("time", 0L).takeIf { it > 0 } ?: return null
        fun d(k: String) = prefs.getString(k, null)?.toDoubleOrNull()
        return DeviceStatus(time, d("reservoir"), d("pumpBattery"), d("pumpVolt"), d("uploaderBattery"), prefs.getString("pumpStatus", null))
    }
}
