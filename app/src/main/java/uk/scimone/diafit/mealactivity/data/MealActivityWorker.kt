package uk.scimone.diafit.mealactivity.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uk.scimone.diafit.core.data.local.AppDatabase
import java.util.concurrent.TimeUnit

/**
 * Runs the meal-activity pipeline for the user. Retryable: any failure (or a run that detected its data changing
 * underneath it) returns retry, and the pipeline is idempotent, so a repeat cannot corrupt or regress curves.
 */
class MealActivityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val service: MealActivityService by inject()

    override suspend fun doWork(): Result = try {
        val report = service.runForUser(MealActivityScheduler.USER_ID, force = inputData.getBoolean(KEY_FORCE, false))
        if (report.needsRerun && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    } catch (e: Exception) {
        Log.e(MealActivityService.TAG, "Pipeline failed", e)
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        const val KEY_FORCE = "force"
        private const val MAX_ATTEMPTS = 5
    }
}

/**
 * Coalescing triggers. Meal changes run soon (5 s, appended behind a pending run); CGM changes arrive every minute, so
 * at most one run is queued per 15 minutes (KEEP). All work is unique-named and persisted by WorkManager. The queue is an
 * optimisation: the pipeline derives "what is pending" from persisted data (source revision vs last processed), so a
 * trigger lost to a process death is repaired by the periodic run.
 */
class MealActivityScheduler(private val context: Context) {
    private val wm get() = WorkManager.getInstance(context)

    fun onMealsChanged() {
        wm.enqueueUniqueWork(
            "meal_activity_meals", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<MealActivityWorker>().setInitialDelay(5, TimeUnit.SECONDS).build()
        )
    }

    fun onCgmChanged() {
        wm.enqueueUniqueWork(
            "meal_activity_cgm", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MealActivityWorker>().setInitialDelay(15, TimeUnit.MINUTES).build()
        )
    }

    fun checkNow() = wm.enqueueUniqueWork("meal_activity_now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<MealActivityWorker>().build())

    fun schedulePeriodic() {
        wm.enqueueUniquePeriodicWork(
            "meal_activity_periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MealActivityWorker>(6, TimeUnit.HOURS).build()
        )
    }

    companion object { const val USER_ID = 1 }
}

/**
 * The common invalidation mechanism: Room's invalidation tracker reports every committed write to the source tables,
 * whichever code path made it (manual entry, AAPS/xDrip/Juggluco/Nightscout broadcasts, Health Connect, backfill, edits,
 * soft deletes), so no ingestion path has to remember to notify anyone. Bursts (a big import) are debounced into one job.
 */
@OptIn(FlowPreview::class)
class MealActivityInvalidationObserver(private val db: AppDatabase, private val scheduler: MealActivityScheduler) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            db.invalidationTracker.createFlow("MealEntity", emitInitialState = false).debounce(3_000).collect { scheduler.onMealsChanged() }
        }
        scope.launch {
            db.invalidationTracker.createFlow("CgmEntity", emitInitialState = false).debounce(60_000).collect { scheduler.onCgmChanged() }
        }
    }
}
