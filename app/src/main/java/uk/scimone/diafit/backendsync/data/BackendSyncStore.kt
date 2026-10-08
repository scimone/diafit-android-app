package uk.scimone.diafit.backendsync.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BackendConfig(val baseUrl: String = "", val token: String = "") {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
}

data class BackendSyncStatus(
    val lastSyncAt: Long? = null,
    val lastOk: Boolean = true,
    val message: String? = null,
    val running: Boolean = false
)

/** What a meal looked like when it was last uploaded, so edits can be detected and patched. */
data class UploadedMeal(val hash: Int, val mealTimeUtc: Long)

enum class SyncCursor { CGM, BOLUS, HEART_RATE, SLEEP }

/**
 * Backend address, token, upload progress and the last sync's outcome. Plain SharedPreferences, like the
 * Nightscout/AI settings: not secret storage.
 */
class BackendSyncStore(context: Context) {
    private val prefs = context.getSharedPreferences("backend_sync", Context.MODE_PRIVATE)
    private val mealPrefs = context.getSharedPreferences("backend_sync_meals", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(BackendConfig(prefs.getString(KEY_URL, "")!!, prefs.getString(KEY_TOKEN, "")!!))
    val config: StateFlow<BackendConfig> = _config.asStateFlow()

    private val _status = MutableStateFlow(
        BackendSyncStatus(
            lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 },
            lastOk = prefs.getBoolean(KEY_LAST_OK, true),
            message = prefs.getString(KEY_MESSAGE, null)
        )
    )
    val status: StateFlow<BackendSyncStatus> = _status.asStateFlow()

    /** A different server or account starts the upload over (the bulk endpoints skip what already exists). */
    fun setConfig(config: BackendConfig) {
        val old = _config.value
        val normalized = config.copy(baseUrl = config.baseUrl.trim(), token = config.token.trim())
        if (normalized == old) return
        prefs.edit {
            putString(KEY_URL, normalized.baseUrl)
            putString(KEY_TOKEN, normalized.token)
            SyncCursor.entries.forEach { remove(cursorKey(it)) }
            remove(KEY_LAST_SYNC); remove(KEY_LAST_OK); remove(KEY_MESSAGE)
        }
        mealPrefs.edit { clear() }
        _config.value = normalized
        _status.value = BackendSyncStatus()
    }

    fun cursor(cursor: SyncCursor): Int = prefs.getInt(cursorKey(cursor), 0)
    fun setCursor(cursor: SyncCursor, id: Int) = prefs.edit { putInt(cursorKey(cursor), id) }

    fun uploadedMeals(): Map<String, UploadedMeal> = mealPrefs.all.mapNotNull { (key, value) ->
        val parts = (value as? String)?.split('|') ?: return@mapNotNull null
        val hash = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val time = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        key to UploadedMeal(hash, time)
    }.toMap()

    fun setUploadedMeals(meals: Map<String, UploadedMeal>) = mealPrefs.edit {
        meals.forEach { (key, m) -> putString(key, "${m.hash}|${m.mealTimeUtc}") }
    }

    fun setRunning(running: Boolean) {
        _status.value = _status.value.copy(running = running)
    }

    fun recordResult(ok: Boolean, message: String, at: Long) {
        prefs.edit {
            putLong(KEY_LAST_SYNC, at)
            putBoolean(KEY_LAST_OK, ok)
            putString(KEY_MESSAGE, message)
        }
        _status.value = BackendSyncStatus(at, ok, message, running = _status.value.running)
    }

    private fun cursorKey(cursor: SyncCursor) = "cursor_${cursor.name}"

    private companion object {
        const val KEY_URL = "base_url"
        const val KEY_TOKEN = "token"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_LAST_OK = "last_ok"
        const val KEY_MESSAGE = "last_message"
    }
}
