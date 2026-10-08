package uk.scimone.diafit.backendsync.data

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/** One upload run (see [BackendSyncer]); does nothing when no backend is configured. */
class BackendSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val syncer: BackendSyncer by inject()

    override suspend fun doWork(): Result = try {
        when (val result = syncer.sync(USER_ID)) {
            null, is BackendResult.Ok -> Result.success()
            // Unreachable server / server error: let WorkManager back off. A rejected token or bad data won't
            // fix itself, so wait for the next periodic run (the error is shown in Settings).
            is BackendResult.Failed -> if (result.status == null || result.status >= 500) Result.retry() else Result.success()
        }
    } catch (e: Exception) {
        Log.e("BackendSync", "Worker failed", e)
        Result.retry()
    }

    private companion object {
        const val USER_ID = 1
    }
}

/** Every 15 minutes (WorkManager's minimum) while online, plus on app open and after the settings change. */
class BackendSyncScheduler(private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackendSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
        )
    }

    fun syncNow() {
        workManager.enqueueUniqueWork(
            ONE_TIME, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BackendSyncWorker>()
                .setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(PERIODIC)
        workManager.cancelUniqueWork(ONE_TIME)
    }

    private companion object {
        const val PERIODIC = "backend_sync_periodic"
        const val ONE_TIME = "backend_sync_now"
    }
}
