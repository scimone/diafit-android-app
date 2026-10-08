package uk.scimone.diafit.core.data.nightscout

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.scimone.diafit.core.domain.model.DeviceKind
import uk.scimone.diafit.core.domain.model.DeviceLifetimes

/** The lifetime (hours) the user set for each consumable, in preferences. */
class DeviceLifetimeStore(context: Context) {
    private val prefs = context.getSharedPreferences("device_lifetimes", Context.MODE_PRIVATE)
    private val _lifetimes = MutableStateFlow(read())
    val lifetimes: StateFlow<DeviceLifetimes> = _lifetimes

    fun set(kind: DeviceKind, hours: Int) {
        prefs.edit().putInt(kind.name, hours.coerceIn(1, 24 * 365)).apply()
        _lifetimes.value = read()
    }

    private fun read(): DeviceLifetimes = DeviceKind.configurable.associateWith { prefs.getInt(it.name, it.defaultHours) }
}
