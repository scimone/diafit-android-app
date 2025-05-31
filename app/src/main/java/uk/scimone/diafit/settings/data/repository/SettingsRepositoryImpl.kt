package uk.scimone.diafit.settings.data.repository

import android.content.Context
import androidx.preference.PreferenceManager
import uk.scimone.diafit.settings.domain.model.CgmSource
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
}
