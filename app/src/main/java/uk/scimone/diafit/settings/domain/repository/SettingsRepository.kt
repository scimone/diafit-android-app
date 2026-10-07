package uk.scimone.diafit.settings.domain.repository

import uk.scimone.diafit.settings.domain.model.AiConfig
import uk.scimone.diafit.settings.domain.model.BolusSource
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.model.NightscoutConfig
import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange

interface SettingsRepository {
    suspend fun getCgmSource(): CgmSource
    suspend fun setCgmSource(source: CgmSource)
    suspend fun getBolusSource(): BolusSource
    suspend fun setBolusSource(source: BolusSource)

    suspend fun getTargetRange(): SettingsGlucoseTargetRange
    suspend fun setTargetRange(range: SettingsGlucoseTargetRange)

    suspend fun getNightscoutConfig(): NightscoutConfig
    suspend fun setNightscoutConfig(config: NightscoutConfig)

    suspend fun getAiConfig(): AiConfig
    suspend fun setAiConfig(config: AiConfig)

    /** Health Connect activity import (heart rate, steps, sleep, exercise) switched on by the user. */
    suspend fun isHealthConnectEnabled(): Boolean
    suspend fun setHealthConnectEnabled(enabled: Boolean)
    /** Epoch ms of the last successful Health Connect import, or null if there was none. */
    suspend fun getHealthConnectLastSync(): Long?
    suspend fun setHealthConnectLastSync(time: Long?)

    /** What the last Health Connect import found, encoded by the importer; null if none ran yet. */
    suspend fun getHealthConnectSummary(): String?
    suspend fun setHealthConnectSummary(summary: String?)
}
