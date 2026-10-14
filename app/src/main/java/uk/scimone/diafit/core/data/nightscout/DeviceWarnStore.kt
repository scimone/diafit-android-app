package uk.scimone.diafit.core.data.nightscout

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.scimone.diafit.core.domain.model.DeviceKind
import uk.scimone.diafit.core.domain.model.WarnLevels
import uk.scimone.diafit.core.domain.model.WarnThresholds

/** The yellow / red warning thresholds the user set per consumable (hours before expiry, units for insulin). */
class DeviceWarnStore(context: Context) {
    private val prefs = context.getSharedPreferences("device_warnings", Context.MODE_PRIVATE)
    private val _thresholds = MutableStateFlow(read())
    val thresholds: StateFlow<WarnThresholds> = _thresholds

    private val _alertsEnabled = MutableStateFlow(prefs.getBoolean("alerts_enabled", true))
    /** Whether expiry alerts are raised at all (bell and push). */
    val alertsEnabled: StateFlow<Boolean> = _alertsEnabled

    fun setAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("alerts_enabled", enabled).apply()
        _alertsEnabled.value = enabled
    }

    fun set(kind: DeviceKind, levels: WarnLevels) {
        val k = if (kind == DeviceKind.PATCH) DeviceKind.SITE else kind
        prefs.edit().putInt("${k.name}_initial", levels.initial).putInt("${k.name}_urgent", levels.urgent).apply()
        _thresholds.value = read()
    }

    private fun read() = WarnThresholds(WarnThresholds.defaults.mapValues { (k, d) ->
        WarnLevels(prefs.getInt("${k.name}_initial", d.initial), prefs.getInt("${k.name}_urgent", d.urgent))
    })
}
