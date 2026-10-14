package uk.scimone.diafit.dailyrecap.data

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The 07:00 job: writes yesterday's recap and pushes it, then queues the next morning's job. A manual run
 * ([KEY_EPOCH_DAY], used by the debug trigger) writes that day's recap instead and always pushes it.
 */
class DailyRecapWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val service: DailyRecapService by inject()
    private val store: DailyRecapStore by inject()
    private val scheduler: DailyRecapScheduler by inject()

    override suspend fun doWork(): Result {
        val manualDay = inputData.getLong(KEY_EPOCH_DAY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
        if (manualDay == null && !store.config.value.enabled) return Result.success() // switched off: the chain ends here
        val date = manualDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now().minusDays(1)
        if (manualDay == null) store.update { it.copy(nextRunOverrideUtc = null) } // a one-time run time is used up
        // A second run for a day that already has its recap (app restarted, job retried) must not push it twice.
        if (manualDay == null && store.recapFor(date.toEpochDay())?.partialDay == false) {
            scheduler.scheduleNext()
            return Result.success()
        }
        val result = service.generate(date)
        if (result.isFailure && manualDay == null && runAttemptCount < MAX_ATTEMPTS) return Result.retry()
        if (result.isFailure) Log.w(TAG, "Recap for $date not written: ${result.exceptionOrNull()?.message}")
        if (manualDay == null) scheduler.scheduleNext()
        return Result.success()
    }

    companion object {
        const val KEY_EPOCH_DAY = "epoch_day"
        private const val TAG = "DailyRecap"
        private const val MAX_ATTEMPTS = 3
    }
}

/**
 * Keeps exactly one job queued for the next 07:00 (local time, so daylight saving is followed because the delay is
 * recomputed each time). WorkManager is not exact: with the phone in Doze the push can come a little after 07:00.
 */
class DailyRecapScheduler(private val context: Context, private val store: DailyRecapStore) {
    private val workManager get() = WorkManager.getInstance(context)
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Queues the next run (at the configured time, or the one-time override) unless one is already waiting. */
    fun scheduleNext(zone: ZoneId = ZoneId.systemDefault()) {
        val config = store.config.value
        val now = LocalDateTime.now(zone)
        val override = config.nextRunOverrideUtc?.takeIf { it > 0 }
        val delay = if (override != null) (override - System.currentTimeMillis()).coerceAtLeast(0) else delayUntilNextRun(zone, now, LocalTime.of(config.hour, config.minute))
        // One unique name per target moment: the running job can queue the next one without KEEP seeing itself.
        val target = if (override != null) "o$override" else now.plusNanos(delay * 1_000_000).toLocalDate().toString()
        val request = OneTimeWorkRequestBuilder<DailyRecapWorker>().setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).addTag(TAG).build()
        workManager.enqueueUniqueWork("$UNIQUE$target", ExistingWorkPolicy.KEEP, request)
    }

    /** After the time (or the override) changed: drop what is queued and queue again. */
    fun reschedule() { cancel(); scheduleNext() }

    fun cancel() = workManager.cancelAllWorkByTag(TAG)

    /** Debug/test: writes and pushes the recap of [epochDay] now. */
    fun runNow(epochDay: Long) {
        val request = OneTimeWorkRequestBuilder<DailyRecapWorker>().setConstraints(online)
            .setInputData(workDataOf(DailyRecapWorker.KEY_EPOCH_DAY to epochDay)).build()
        workManager.enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        private const val UNIQUE = "daily_recap_"
        private const val TAG = "daily_recap_chain"
        private const val UNIQUE_NOW = "daily_recap_now"
        val RUN_AT: LocalTime = LocalTime.of(7, 0)

        /** Milliseconds from [now] to the next [RUN_AT]; a run time that has just passed means tomorrow. */
        fun delayUntilNextRun(zone: ZoneId, now: LocalDateTime = LocalDateTime.now(zone), at: LocalTime = RUN_AT): Long {
            var next = now.toLocalDate().atTime(at)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return java.time.Duration.between(now, next).toMillis()
        }
    }
}
