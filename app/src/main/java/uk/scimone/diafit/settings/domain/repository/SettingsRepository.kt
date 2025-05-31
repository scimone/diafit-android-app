package uk.scimone.diafit.settings.domain.repository

import uk.scimone.diafit.settings.domain.model.CgmSource

interface SettingsRepository {
    suspend fun getCgmSource(): CgmSource
    suspend fun setCgmSource(source: CgmSource)
}
