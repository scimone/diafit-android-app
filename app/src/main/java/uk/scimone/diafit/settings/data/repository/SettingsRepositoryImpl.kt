package uk.scimone.diafit.settings.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import uk.scimone.diafit.settings.domain.model.BolusSource
import uk.scimone.diafit.settings.domain.model.CgmSource
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
}
