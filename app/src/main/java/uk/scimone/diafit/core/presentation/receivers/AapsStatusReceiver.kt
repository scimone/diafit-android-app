package uk.scimone.diafit.core.presentation.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource.AapsTempBasalParser
import uk.scimone.diafit.core.data.worker.TempBasalBroadcastWorker
import androidx.work.WorkManager

/**
 * AAPS "Data broadcaster" status (`info.nightscout.androidaps.status`). The full extras (predictions, console
 * log) are far larger than WorkManager's 10 KB input limit, so only the enacted temp basal is forwarded.
 */
class AapsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        val tempBasal = AapsTempBasalParser.parse(
            extras.getString("enacted"),
            extras.getLong("enactedTimeStamp", 0L),
            extras.getDouble("baseBasal", Double.NaN)
        ) ?: return
        val data = Data.Builder()
            .putLong(TempBasalBroadcastWorker.KEY_ENACTED_AT, tempBasal.enactedAt)
            .putDouble(TempBasalBroadcastWorker.KEY_RATE, tempBasal.rate)
            .putDouble(TempBasalBroadcastWorker.KEY_DURATION, tempBasal.durationMinutes)
            .putDouble(TempBasalBroadcastWorker.KEY_BASE_BASAL, tempBasal.baseBasal ?: Double.NaN)
            .build()
        val request = OneTimeWorkRequestBuilder<TempBasalBroadcastWorker>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("temp-basal-ingest", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
