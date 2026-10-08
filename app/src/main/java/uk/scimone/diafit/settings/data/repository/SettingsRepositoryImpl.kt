package uk.scimone.diafit.settings.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import uk.scimone.diafit.BuildConfig
import uk.scimone.diafit.settings.domain.model.AiConfig
import uk.scimone.diafit.settings.domain.model.DEFAULT_AI_MODEL
import uk.scimone.diafit.settings.domain.model.BolusSource
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.model.Connector
import uk.scimone.diafit.settings.domain.model.DataType
import uk.scimone.diafit.settings.domain.model.NightscoutConfig
import uk.scimone.diafit.settings.domain.model.BasalStyle
import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange
import uk.scimone.diafit.settings.domain.repository.SettingsRepository

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    init { migrateLegacySources(); migrateProfileAndDevice(); migrateDeviceStatus() }

    /** First run of the connector model: carry over the old single CGM / bolus source and the Health Connect switch. */
    private fun migrateLegacySources() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val cgm = prefs.getString("cgm_source", CgmSource.NIGHTSCOUT.name)
            ?.let { runCatching { CgmSource.valueOf(it) }.getOrNull() } ?: CgmSource.NIGHTSCOUT
        val cgmConnector = when (cgm) {
            CgmSource.NIGHTSCOUT -> Connector.NIGHTSCOUT
            CgmSource.XDRIP -> Connector.XDRIP
            CgmSource.JUGGLUCO -> Connector.JUGGLUCO
            CgmSource.HEALTH_CONNECT -> Connector.HEALTH_CONNECT
        }
        val hc = prefs.getBoolean("health_connect_enabled", false) || cgm == CgmSource.HEALTH_CONNECT
        val enabled = mutableSetOf(cgmConnector, Connector.AAPS)
        if (hc) enabled += Connector.HEALTH_CONNECT
        val edit = prefs.edit()
            .putStringSet(KEY_ENABLED, enabled.map { it.name }.toSet())
            .putString(selectionKey(DataType.CGM), cgmConnector.name)
            .putBoolean("health_connect_enabled", hc)
        listOf(DataType.BOLUS, DataType.FOOD, DataType.BASAL).forEach { edit.putString(selectionKey(it), Connector.AAPS.name) }
        if (hc) DataType.ACTIVITY.forEach { edit.putString(selectionKey(it), Connector.HEALTH_CONNECT.name) }
        edit.putBoolean(KEY_MIGRATED, true).apply()
    }

    /** Profile / device events used to be stored whenever AAPS was connected; keep that by selecting AAPS (else Nightscout) for them. */
    private fun migrateProfileAndDevice() {
        if (prefs.getBoolean(KEY_MIGRATED_PROFILE, false)) return
        val enabled = prefs.getStringSet(KEY_ENABLED, emptySet()).orEmpty()
        val edit = prefs.edit()
        for (type in listOf(DataType.PROFILE, DataType.DEVICE)) {
            val pick = listOf(Connector.AAPS, Connector.NIGHTSCOUT).firstOrNull { it.name in enabled }
            if (pick != null && prefs.getString(selectionKey(type), null) == null) edit.putString(selectionKey(type), pick.name)
        }
        edit.putBoolean(KEY_MIGRATED_PROFILE, true).apply()
    }

    /** Device status is new: existing Nightscout users get it switched on. */
    private fun migrateDeviceStatus() {
        if (prefs.getBoolean(KEY_MIGRATED_DEVICE_STATUS, false)) return
        val enabled = prefs.getStringSet(KEY_ENABLED, emptySet()).orEmpty()
        val edit = prefs.edit()
        if (Connector.NIGHTSCOUT.name in enabled && prefs.getString(selectionKey(DataType.DEVICE_STATUS), null) == null)
            edit.putString(selectionKey(DataType.DEVICE_STATUS), Connector.NIGHTSCOUT.name)
        edit.putBoolean(KEY_MIGRATED_DEVICE_STATUS, true).apply()
    }

    override suspend fun getEnabledConnectors(): Set<Connector> =
        prefs.getStringSet(KEY_ENABLED, emptySet()).orEmpty()
            .mapNotNull { n -> Connector.values().firstOrNull { it.name == n } }.toSet()

    override suspend fun setConnectorEnabled(connector: Connector, enabled: Boolean) {
        val now = getEnabledConnectors().let { if (enabled) it + connector else it - connector }
        val edit = prefs.edit().putStringSet(KEY_ENABLED, now.map { it.name }.toSet())
        for (type in connector.provides) {
            val current = prefs.getString(selectionKey(type), null)
            if (enabled) {
                // Only fills a type nobody feeds yet; an explicit "off" or another connector's choice stays.
                if (current == null) edit.putString(selectionKey(type), connector.name)
            } else if (current == connector.name) {
                val next = Connector.values().firstOrNull { it in now && type in it.provides }
                if (next != null) edit.putString(selectionKey(type), next.name) else edit.remove(selectionKey(type))
            }
        }
        if (connector == Connector.HEALTH_CONNECT) edit.putBoolean("health_connect_enabled", enabled)
        edit.apply()
    }

    override suspend fun getSelection(type: DataType): Connector? {
        val name = prefs.getString(selectionKey(type), null) ?: return null
        val connector = Connector.values().firstOrNull { it.name == name } ?: return null
        return connector.takeIf { it in getEnabledConnectors() && type in it.provides }
    }

    override suspend fun setSelection(type: DataType, connector: Connector?) {
        prefs.edit().putString(selectionKey(type), connector?.name ?: SELECTION_OFF).apply()
    }

    override suspend fun isActivityEnabled(): Boolean = DataType.ACTIVITY.any { getSelection(it) != null }

    override suspend fun getCgmSource(): CgmSource? = getSelection(DataType.CGM)?.toCgmSource()

    override suspend fun getBolusSource(): BolusSource? =
        if (getSelection(DataType.BOLUS) == Connector.AAPS) BolusSource.AAPS else null

    override suspend fun getTargetRange(): SettingsGlucoseTargetRange {
        val lower = prefs.getInt("glucose_lower_bound", 70)
        val upper = prefs.getInt("glucose_upper_bound", 180)
        return SettingsGlucoseTargetRange(lower, upper)
    }

    override suspend fun setTargetRange(range: SettingsGlucoseTargetRange) {
        prefs.edit()
            .putInt("glucose_lower_bound", range.lowerBound)
            .putInt("glucose_upper_bound", range.upperBound)
            .apply()
    }

    override suspend fun getBasalStyle(): BasalStyle =
        BasalStyle.values().firstOrNull { it.name == prefs.getString("basal_style", null) } ?: BasalStyle.RATE

    override suspend fun setBasalStyle(style: BasalStyle) {
        prefs.edit().putString("basal_style", style.name).apply()
    }

    override suspend fun getNightscoutConfig(): NightscoutConfig {
        val baseUrl = prefs.getString("nightscout_base_url", BuildConfig.BASE_URL) ?: BuildConfig.BASE_URL
        val apiKey = prefs.getString("nightscout_api_key", BuildConfig.API_KEY) ?: BuildConfig.API_KEY
        return NightscoutConfig(baseUrl = baseUrl, apiKey = apiKey)
    }

    override suspend fun setNightscoutConfig(config: NightscoutConfig) {
        prefs.edit()
            .putString("nightscout_base_url", config.baseUrl)
            .putString("nightscout_api_key", config.apiKey)
            .apply()
    }

    override suspend fun getAiConfig(): AiConfig {
        val baseUrl = prefs.getString("ai_base_url", DEFAULT_AI_BASE_URL) ?: DEFAULT_AI_BASE_URL
        val apiKey = prefs.getString("ai_api_key", "") ?: ""
        val model = prefs.getString("ai_model", DEFAULT_AI_MODEL) ?: DEFAULT_AI_MODEL
        return AiConfig(baseUrl = baseUrl, apiKey = apiKey, model = model)
    }

    override suspend fun setAiConfig(config: AiConfig) {
        prefs.edit()
            .putString("ai_base_url", config.baseUrl)
            .putString("ai_api_key", config.apiKey)
            .putString("ai_model", config.model)
            .apply()
    }

    override suspend fun isHealthConnectEnabled(): Boolean = prefs.getBoolean("health_connect_enabled", false)

    override suspend fun setHealthConnectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("health_connect_enabled", enabled).apply()
    }

    override suspend fun getHealthConnectLastSync(): Long? =
        prefs.getLong("health_connect_last_sync", 0L).takeIf { it > 0L }

    override suspend fun setHealthConnectLastSync(time: Long?) {
        prefs.edit().putLong("health_connect_last_sync", time ?: 0L).apply()
    }

    override suspend fun getHealthConnectSummary(): String? = prefs.getString("health_connect_summary", null)

    override suspend fun setHealthConnectSummary(summary: String?) {
        prefs.edit().putString("health_connect_summary", summary).apply()
    }

    private companion object {
        const val KEY_MIGRATED = "connectors_migrated"
        const val KEY_MIGRATED_DEVICE_STATUS = "connectors_migrated_device_status"
        const val KEY_MIGRATED_PROFILE = "connectors_migrated_profile_device"
        const val KEY_ENABLED = "connectors_enabled"
        const val SELECTION_OFF = "OFF"
        fun selectionKey(type: DataType) = "source_${type.name.lowercase()}"
        const val DEFAULT_AI_BASE_URL = "https://api.openai.com/v1"
    }
}
