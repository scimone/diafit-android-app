package uk.scimone.diafit.core.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource.AapsTempBasalParser
import uk.scimone.diafit.core.domain.repository.PumpEventRepository
import uk.scimone.diafit.settings.domain.repository.SettingsRepository
import uk.scimone.diafit.settings.domain.model.Connector
import uk.scimone.diafit.settings.domain.model.DataType

/** Stores the temp basal the AAPS loop enacted (from the status broadcast) as a "Temp Basal" [uk.scimone.diafit.core.domain.model.PumpEventEntity]. */
class TempBasalBroadcastWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val repository: PumpEventRepository by inject()
    private val settings: SettingsRepository by inject()

    override suspend fun doWork(): Result {
        if (settings.getSelection(DataType.BASAL) != Connector.AAPS) return Result.success()
        val enactedAt = inputData.getLong(KEY_ENACTED_AT, 0L)
        val rate = inputData.getDouble(KEY_RATE, Double.NaN)
        val duration = inputData.getDouble(KEY_DURATION, Double.NaN)
        if (enactedAt <= 0 || rate.isNaN() || duration.isNaN()) return Result.success()
        val tempBasal = AapsTempBasalParser.TempBasal(
            enactedAt, rate, duration, inputData.getDouble(KEY_BASE_BASAL, Double.NaN).takeIf { !it.isNaN() }
        )
        return try {
            val inserted = repository.insert(AapsTempBasalParser.toEvent(tempBasal))
            if (inserted) Log.i(TAG, "Temp basal ${tempBasal.rate} U/h for ${tempBasal.durationMinutes} min stored")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Insert failed", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_RATE = "rate"
        const val KEY_DURATION = "duration"
        const val KEY_ENACTED_AT = "enactedAt"
        const val KEY_BASE_BASAL = "baseBasal"
        private const val TAG = "TempBasalWorker"
    }
}
