package uk.scimone.diafit.core.presentation.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uk.scimone.diafit.core.data.nightscout.DeviceStatusStore
import uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource.AapsDeviceStatusParser
import uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource.AapsTempBasalParser
import uk.scimone.diafit.core.data.worker.TempBasalBroadcastWorker
import uk.scimone.diafit.settings.domain.model.Connector
import uk.scimone.diafit.settings.domain.model.DataType
import uk.scimone.diafit.settings.domain.repository.SettingsRepository
import androidx.work.WorkManager

/**
 * AAPS "Data broadcaster" status (`info.nightscout.androidaps.status`). The full extras (predictions, console
 * log) are far larger than WorkManager's 10 KB input limit, so they are parsed in place: the enacted temp basal
 * is forwarded to a worker, the pump reservoir / battery go straight to [DeviceStatusStore].
 */
class AapsStatusReceiver : BroadcastReceiver(), KoinComponent {
    private val settings: SettingsRepository by inject()
    private val deviceStatus: DeviceStatusStore by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        // Names (and short values) of every extra, so the real reservoir / battery keys can be confirmed.
        Log.i(TAG, extras.keySet().joinToString { k ->
            val v = extras.get(k)
            if (v is String && v.length > 40) "$k=<${v.length} chars>" else "$k=$v"
        })

        val levels = AapsDeviceStatusParser.parse(extras.keySet().associateWith { extras.get(it) }, System.currentTimeMillis())
        if (levels != null) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (settings.getSelection(DataType.DEVICE) == Connector.AAPS) deviceStatus.merge(levels)
                } finally { pending.finish() }
            }
        }

        val tempBasal = AapsTempBasalParser.parse(
            extras.getString("enacted"),
            extras.getLong("enactedTimeStamp", 0L),
            (extras.get("baseBasal") as? Number)?.toDouble()
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

    private companion object { const val TAG = "AapsStatus" }
}
