package uk.scimone.diafit.settings.domain.repository

import uk.scimone.diafit.settings.domain.model.BolusSource
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.model.SettingsGlucoseTargetRange

interface SettingsRepository {
    suspend fun getCgmSource(): CgmSource
    suspend fun setCgmSource(source: CgmSource)
    suspend fun getBolusSource(): BolusSource
    suspend fun setBolusSource(source: BolusSource)

    suspend fun getTargetRange(): SettingsGlucoseTargetRange
    suspend fun setTargetRange(range: SettingsGlucoseTargetRange)
}
