package uk.scimone.diafit.dailyrecap.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import uk.scimone.diafit.dailyrecap.domain.DailyRecap

/** The user's choices for the recap; it is off until switched on. [connectionId] null = the first AI connection. */
data class DailyRecapConfig(val enabled: Boolean = false, val connectionId: String? = null, val model: String = "")

/** Settings and the last two weeks of recaps, in plain preferences (a recap is a few KB of JSON). */
class DailyRecapStore(context: Context) {
    private val prefs = context.getSharedPreferences("daily_recap", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<DailyRecapConfig> = _config

    private val _recaps = MutableStateFlow(readRecaps())
    /** Newest first. */
    val recaps: StateFlow<List<DailyRecap>> = _recaps

    fun update(change: (DailyRecapConfig) -> DailyRecapConfig) {
        val next = change(_config.value)
        prefs.edit().putBoolean(ENABLED, next.enabled).putString(CONNECTION, next.connectionId).putString(MODEL, next.model).apply()
        _config.value = next
    }

    fun recapFor(epochDay: Long): DailyRecap? = _recaps.value.firstOrNull { it.epochDay == epochDay }

    fun save(recap: DailyRecap) {
        val all = (listOf(recap) + _recaps.value.filter { it.epochDay != recap.epochDay }).sortedByDescending { it.epochDay }.take(KEEP)
        prefs.edit().putString(RECAPS, json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DailyRecap.serializer()), all)).apply()
        _recaps.value = all
    }

    private fun readConfig() = DailyRecapConfig(
        enabled = prefs.getBoolean(ENABLED, false),
        connectionId = prefs.getString(CONNECTION, null),
        model = prefs.getString(MODEL, "").orEmpty()
    )

    private fun readRecaps(): List<DailyRecap> = runCatching {
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(DailyRecap.serializer()), prefs.getString(RECAPS, null) ?: "[]")
    }.getOrDefault(emptyList())

    private companion object {
        const val ENABLED = "enabled"
        const val CONNECTION = "connection_id"
        const val MODEL = "model"
        const val RECAPS = "recaps"
        const val KEEP = 14
    }
}
