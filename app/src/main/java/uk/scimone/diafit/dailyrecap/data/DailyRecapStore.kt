package uk.scimone.diafit.dailyrecap.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import uk.scimone.diafit.dailyrecap.domain.DailyRecap

/**
 * The user's choices for the recap. [connectionId] null = the first AI connection; [prompt] blank = the built-in one.
 * [push] = also push it to the phone (it always lands in the bell). [nextRunOverrideUtc] makes the next run happen at
 * that moment instead of the usual time, once.
 */
data class DailyRecapConfig(
    val enabled: Boolean = true,
    val connectionId: String? = null,
    val model: String = "",
    val prompt: String = "",
    val push: Boolean = true,
    val hour: Int = 7,
    val minute: Int = 0,
    val nextRunOverrideUtc: Long? = null
)

/** Settings and the last two weeks of recaps, in plain preferences (a recap is a few KB of JSON). */
class DailyRecapStore(context: Context) {
    private val hadAiSetUp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context).contains("ai_connections")
    private val prefs = context.getSharedPreferences("daily_recap", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<DailyRecapConfig> = _config

    private val _recaps = MutableStateFlow(readRecaps())
    /** Newest first. */
    val recaps: StateFlow<List<DailyRecap>> = _recaps

    fun update(change: (DailyRecapConfig) -> DailyRecapConfig) {
        val next = change(_config.value)
        prefs.edit().putBoolean(ENABLED, next.enabled).putString(CONNECTION, next.connectionId).putString(MODEL, next.model)
            .putString(PROMPT, next.prompt).putBoolean(PUSH, next.push).putInt(HOUR, next.hour).putInt(MINUTE, next.minute)
            .putLong(OVERRIDE, next.nextRunOverrideUtc ?: 0L).apply()
        _config.value = next
    }

    fun recapFor(epochDay: Long): DailyRecap? = _recaps.value.firstOrNull { it.epochDay == epochDay }

    fun save(recap: DailyRecap) {
        val all = (listOf(recap) + _recaps.value.filter { it.epochDay != recap.epochDay }).sortedByDescending { it.epochDay }.take(KEEP)
        prefs.edit().putString(RECAPS, json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DailyRecap.serializer()), all)).apply()
        _recaps.value = all
    }

    private fun readConfig() = DailyRecapConfig(
        // AI is off by default, and so is the recap; installs that already set up an AI connection keep it on.
        enabled = prefs.getBoolean(ENABLED, hadAiSetUp),
        prompt = prefs.getString(PROMPT, "").orEmpty(),
        push = prefs.getBoolean(PUSH, true),
        hour = prefs.getInt(HOUR, 7), minute = prefs.getInt(MINUTE, 0),
        nextRunOverrideUtc = prefs.getLong(OVERRIDE, 0L).takeIf { it > 0 },
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
        const val PROMPT = "prompt"
        const val PUSH = "push"
        const val HOUR = "hour"
        const val MINUTE = "minute"
        const val OVERRIDE = "override_utc"
        const val RECAPS = "recaps"
        const val KEEP = 14
    }
}
