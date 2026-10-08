package uk.scimone.diafit.settings.domain.repository

import uk.scimone.diafit.settings.domain.model.AiConfig
import uk.scimone.diafit.settings.domain.model.BasalStyle
import uk.scimone.diafit.settings.domain.model.BolusSource
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.model.Connector
import uk.scimone.diafit.settings.domain.model.DataType
import uk.scimone.diafit.settings.domain.model.NightscoutConfig
import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange

interface SettingsRepository {
    /** Connectors the user has connected (any number). */
    suspend fun getEnabledConnectors(): Set<Connector>
    /**
     * Connects or disconnects [connector]. Connecting makes it the source of every data type it provides
     * that has no source yet; disconnecting hands its types to another connected provider, or to none.
     */
    suspend fun setConnectorEnabled(connector: Connector, enabled: Boolean)
    /** The connector feeding [type], or null when the user turned that data off / nothing is connected. */
    /** Health Connect is connected and at least one activity data type is switched on. */
    suspend fun isActivityEnabled(): Boolean
    suspend fun getSelection(type: DataType): Connector?
    suspend fun setSelection(type: DataType, connector: Connector?)

    /** The CGM connector as the sync layer knows it; null when CGM is off. */
    suspend fun getCgmSource(): CgmSource?
    suspend fun getBolusSource(): BolusSource?

    suspend fun getTargetRange(): SettingsGlucoseTargetRange
    suspend fun setTargetRange(range: SettingsGlucoseTargetRange)

    suspend fun getBasalStyle(): BasalStyle
    suspend fun setBasalStyle(style: BasalStyle)

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
