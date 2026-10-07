package uk.scimone.diafit.core.data.healthconnect

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import android.util.Log
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uk.scimone.diafit.settings.domain.repository.SettingsRepository
import java.util.concurrent.TimeUnit

/** Runs [HealthConnectSyncer.syncActivity], either as the routine recent import or as the 2-week backfill. */
class HealthConnectSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val syncer: HealthConnectSyncer by inject()
    private val settings: SettingsRepository by inject()

    override suspend fun doWork(): Result {
        if (!settings.isHealthConnectEnabled()) return Result.success()
        // A routine run also backfills when no 14-day import has completed yet (the connect-time one can be
        // lost, e.g. the app was killed): the summary of the last successful import records its length.
        val done = HealthConnectImportSummary.decode(settings.getHealthConnectSummary())
        val backfill = inputData.getBoolean(KEY_BACKFILL, false) || done == null || done.days < HealthConnectSyncer.BACKFILL_DAYS
        Log.d(TAG, "Health Connect import (backfill=$backfill)")
        syncer.syncActivity(if (backfill) HealthConnectSyncer.BACKFILL_DAYS else HealthConnectSyncer.RECENT_DAYS)
        return Result.success()
    }

    companion object {
        private const val TAG = "HealthConnectSync"
        const val KEY_BACKFILL = "backfill"
    }
}

/** Schedules the import: every 15 minutes while enabled, plus on demand (app opened, "Sync now", backfill). */
class HealthConnectScheduler(private val context: Context) {

    private val workManager get() = WorkManager.getInstance(context)

    /** Keeps the routine import running; safe to call on every app start. */
    fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    /** Imports now. A backfill replaces a queued routine import; a routine one never interrupts a backfill. */
    fun syncNow(backfill: Boolean = false) {
        workManager.enqueueUniqueWork(
            NOW_WORK,
            if (backfill) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
                .setInputData(Data.Builder().putBoolean(HealthConnectSyncWorker.KEY_BACKFILL, backfill).build())
                .build()
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(PERIODIC_WORK)
        workManager.cancelUniqueWork(NOW_WORK)
    }

    private companion object {
        const val PERIODIC_WORK = "health_connect_sync_periodic"
        const val NOW_WORK = "health_connect_sync_now"
    }
}
