package uk.scimone.diafit.core.data.nightscout

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uk.scimone.diafit.settings.domain.model.Connector
import uk.scimone.diafit.settings.domain.repository.SettingsRepository
import java.util.concurrent.TimeUnit

/**
 * Routine Nightscout import of everything but glucose (which has its own poller): boluses, carbs, temp basals,
 * profile switches / targets and pump / sensor changes, for each type the user took from Nightscout. Re-reads the
 * last couple of days each time, so late uploads and edits are caught; inserts are de-duplicated.
 */
class NightscoutSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val importer: NightscoutTreatmentImporter by inject()
    private val settings: SettingsRepository by inject()

    override suspend fun doWork(): Result {
        if (Connector.NIGHTSCOUT !in settings.getEnabledConnectors()) return Result.success()
        val types = NightscoutTreatmentImporter.TYPES.filter { settings.getSelection(it) == Connector.NIGHTSCOUT }.toSet()
        if (types.isEmpty()) return Result.success()
        val now = System.currentTimeMillis()
        return try {
            val added = importer.import(types, now - RECENT_MS, now + FUTURE_MS)
            Log.d(TAG, "Nightscout sync added $added")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Nightscout sync failed", e)
            Result.success() // the next run retries; a worker retry loop would only hammer an unreachable server
        }
    }

    private companion object {
        const val TAG = "NightscoutSync"
        const val RECENT_MS = 2 * 24 * 3_600_000L
        const val FUTURE_MS = 3_600_000L
    }
}

/** Schedules [NightscoutSyncWorker]: every 15 minutes, plus on demand (app opened, a type switched to Nightscout). */
class NightscoutSyncScheduler(private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NightscoutSyncWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    fun syncNow() {
        workManager.enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<NightscoutSyncWorker>().build())
    }

    private companion object {
        const val PERIODIC_WORK = "nightscout_sync_periodic"
        const val NOW_WORK = "nightscout_sync_now"
    }
}
