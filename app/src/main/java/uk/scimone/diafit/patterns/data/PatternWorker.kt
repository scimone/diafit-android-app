package uk.scimone.diafit.patterns.data

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uk.scimone.diafit.core.domain.model.toSittings
import uk.scimone.diafit.core.domain.repository.ActivityRepository
import uk.scimone.diafit.core.domain.repository.BolusRepository
import uk.scimone.diafit.patterns.domain.ActivityEvents
import uk.scimone.diafit.patterns.domain.ExerciseDetector
import uk.scimone.diafit.core.domain.repository.CgmRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.patterns.domain.MealDetector
import uk.scimone.diafit.history.presentation.model.toThresholds
import uk.scimone.diafit.patterns.domain.DayGlucose
import uk.scimone.diafit.patterns.domain.DetectionInput
import uk.scimone.diafit.patterns.domain.PatternLifecycle
import uk.scimone.diafit.patterns.domain.PatternPipeline
import uk.scimone.diafit.patterns.domain.PatternState
import uk.scimone.diafit.patterns.domain.SleepDetector
import uk.scimone.diafit.patterns.domain.SleepNights
import uk.scimone.diafit.patterns.domain.TrendDetector
import uk.scimone.diafit.patterns.domain.TimeOfDayDetector
import uk.scimone.diafit.notifications.data.AppNotifier
import uk.scimone.diafit.notifications.data.NotificationChannelSpec
import uk.scimone.diafit.notifications.domain.patternLink
import uk.scimone.diafit.notifications.domain.PATTERNS_LINK
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Runs the pattern detectors over the recent CGM history, reconciles the result with what is stored and pushes
 * newly eligible unfavourable patterns. Patterns live on their own page with their own badge, not in the bell list.
 * Every 12 hours and when the app opens.
 */
class PatternWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val cgm: CgmRepository by inject()
    private val mealRepository: MealRepository by inject()
    private val bolusRepository: BolusRepository by inject()
    private val activityRepository: ActivityRepository by inject()
    private val getTargetRange: GetTargetRangeUseCase by inject()
    private val store: PatternStore by inject()
    private val notifier: AppNotifier by inject()

    override suspend fun doWork(): Result {
        return try {
            RUN_LOCK.withLock {
                if (inputData.getBoolean(KEY_RESET, false)) store.replaceAll(emptyList())
                val asOf = inputData.getLong(KEY_AS_OF, 0L)
                if (asOf > 0) scan(asOf, simulated = true) else scan(System.currentTimeMillis(), simulated = false)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Pattern scan failed", e)
            Result.success() // the next run retries
        }
    }

    /**
     * [simulated]: a debug preview as if it were [now] (an earlier moment with complete data): the stored patterns are
     * cleared first, nothing is pushed and nothing is marked as notified.
     */
    private suspend fun scan(now: Long, simulated: Boolean) {
        if (simulated) store.replaceAll(emptyList())
        val from = now - (TimeOfDayDetector.WEEKEND_WINDOW_DAYS + 1) * 24 * 3_600_000L
        val readings = withContext(Dispatchers.IO) { cgm.getEntriesBetween(from, now, USER_ID) }
        val zone = ZoneId.systemDefault()
        val days = DayGlucose.from(readings, zone)
        // Too little data says nothing about which patterns are gone: keep the stored state as it is.
        if (days.size < MIN_DAYS_TO_SCAN) return
        val sittings = withContext(Dispatchers.IO) { mealRepository.getUnmergedSince(USER_ID, from) }.toSittings()
        val boluses = withContext(Dispatchers.IO) { bolusRepository.getBolusBetween(from, now, USER_ID) }
        val activity = withContext(Dispatchers.IO) { activityRepository.getBetween(from, now, USER_ID) }
        val input = DetectionInput(
            days, getTargetRange().toCore().toThresholds(), now, zone, sittings, boluses,
            ActivityEvents.build(activity), SleepNights.build(activity.sleepSessions, zone)
        )

        val stored = store.getAll()
        val candidates = TimeOfDayDetector.detect(input) + MealDetector.detect(input) + ExerciseDetector.detect(input) + SleepDetector.detect(input) + TrendDetector.detect(input)
        val cards = PatternPipeline.finalize(
            candidates,
            existingTips = stored.associate { it.card.id to it.card.tips },
            tipsInUse = stored.filter { it.state != PatternState.RESOLVED }.flatMap { it.card.tips }.toSet()
        )
        val result = PatternLifecycle.reconcile(stored, cards, now)
        store.replaceAll(result.patterns)
        if (result.toNotify.isNotEmpty() && !simulated) {
            val single = result.toNotify.singleOrNull()
            val title = if (single != null) "New pattern" else "${result.toNotify.size} new patterns"
            val text = result.toNotify.joinToString("\n") { it.card.title }
            notifier.pushOnly(CHANNEL, "patterns-${result.toNotify.map { it.card.id }.sorted().hashCode()}", title, text, single?.let { patternLink(it.card.id) } ?: PATTERNS_LINK)
            store.markNotified(result.toNotify.map { it.card.id }, now)
        }
        Log.i(TAG, "events=${input.events.size} (${input.events.count { it.isWorkout }} workouts) nights=${input.nights.size} meals=${sittings.size}")
        Log.i(TAG, "${candidates.size} candidates, ${cards.size} surfaced (${cards.joinToString(",") { it.id }}), ${result.toNotify.size} to notify over ${days.size} days")
    }

    companion object {
        const val KEY_AS_OF = "as_of"
        const val KEY_RESET = "reset"
        private const val TAG = "Patterns"
        private const val USER_ID = 1
        private const val MIN_DAYS_TO_SCAN = 7
        private val RUN_LOCK = Mutex()
        private val CHANNEL = NotificationChannelSpec(
            "GLUCOSE_PATTERNS_CHANNEL", "Glucose patterns",
            "New patterns found in your glucose data", NotificationManager.IMPORTANCE_DEFAULT
        )
    }
}

/** Schedules [PatternWorker]: every 12 hours, plus a scan when the app opens. */
class PatternScheduler(private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedulePeriodic() {
        // Earlier versions' unique work (the old AGP alert worker no longer exists and would fail every run).
        listOf("patterns_periodic", "patterns_now", "insights_periodic", "insights_now").forEach(workManager::cancelUniqueWork)
        workManager.enqueueUniquePeriodicWork(
            "pattern_scan_periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PatternWorker>(12, TimeUnit.HOURS).build()
        )
    }

    /** Debug preview: runs the detectors as if it were [asOfUtc] (see [PatternWorker.scan]). */
    fun simulate(asOfUtc: Long) {
        workManager.enqueueUniqueWork(
            "pattern_scan_sim", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PatternWorker>().setInputData(androidx.work.workDataOf(PatternWorker.KEY_AS_OF to asOfUtc)).build()
        )
    }

    /** Clears every stored pattern and scans the real data again. */
    fun reset() {
        workManager.cancelUniqueWork("pattern_scan_sim")
        workManager.enqueueUniqueWork(
            "pattern_scan_now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PatternWorker>().setInputData(androidx.work.workDataOf(PatternWorker.KEY_RESET to true)).build()
        )
    }

    fun checkNow() {
        workManager.enqueueUniqueWork("pattern_scan_now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<PatternWorker>().build())
    }
}
