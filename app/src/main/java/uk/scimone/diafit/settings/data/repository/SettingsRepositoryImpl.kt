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
import uk.scimone.diafit.settings.domain.model.NightscoutConfig
import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange
import uk.scimone.diafit.settings.domain.repository.SettingsRepository

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    override suspend fun getCgmSource(): CgmSource {
        val name = prefs.getString("cgm_source", CgmSource.NIGHTSCOUT.name)
        return CgmSource.valueOf(name!!)
    }

    override suspend fun setCgmSource(source: CgmSource) {
        prefs.edit().putString("cgm_source", source.name).apply()
    }

    override suspend fun getBolusSource(): BolusSource {
        val name = prefs.getString("bolus_source", BolusSource.AAPS.name)
        return BolusSource.valueOf(name!!)
    }

    override suspend fun setBolusSource(source: BolusSource) {
        prefs.edit().putString("bolus_source", source.name).apply()
    }

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

    private companion object {
        const val DEFAULT_AI_BASE_URL = "https://api.openai.com/v1"
    }
}
