package uk.scimone.diafit.insights.data

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
import uk.scimone.diafit.insights.domain.ActivityEvents
import uk.scimone.diafit.insights.domain.ExerciseDetector
import uk.scimone.diafit.core.domain.repository.CgmRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.insights.domain.MealDetector
import uk.scimone.diafit.history.presentation.model.toThresholds
import uk.scimone.diafit.insights.domain.DayGlucose
import uk.scimone.diafit.insights.domain.DetectionInput
import uk.scimone.diafit.insights.domain.InsightLifecycle
import uk.scimone.diafit.insights.domain.InsightPipeline
import uk.scimone.diafit.insights.domain.InsightState
import uk.scimone.diafit.insights.domain.TimeOfDayDetector
import uk.scimone.diafit.notifications.data.AppNotifier
import uk.scimone.diafit.notifications.data.NotificationChannelSpec
import uk.scimone.diafit.notifications.domain.insightLink
import uk.scimone.diafit.notifications.domain.INSIGHTS_LINK
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Runs the insight detectors over the recent CGM history, reconciles the result with what is stored and pushes
 * newly eligible unfavourable insights. Insights live on their own page with their own badge, not in the bell list.
 * Every 12 hours and when the app opens.
 */
class InsightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val cgm: CgmRepository by inject()
    private val mealRepository: MealRepository by inject()
    private val bolusRepository: BolusRepository by inject()
    private val activityRepository: ActivityRepository by inject()
    private val getTargetRange: GetTargetRangeUseCase by inject()
    private val store: InsightStore by inject()
    private val notifier: AppNotifier by inject()

    override suspend fun doWork(): Result {
        return try {
            RUN_LOCK.withLock { scan() }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Insight scan failed", e)
            Result.success() // the next run retries
        }
    }

    private suspend fun scan() {
        val now = System.currentTimeMillis()
        val from = now - (TimeOfDayDetector.WEEKEND_WINDOW_DAYS + 1) * 24 * 3_600_000L
        val readings = withContext(Dispatchers.IO) { cgm.getEntriesBetween(from, now, USER_ID) }
        val zone = ZoneId.systemDefault()
        val days = DayGlucose.from(readings, zone)
        // Too little data says nothing about which insights are gone: keep the stored state as it is.
        if (days.size < MIN_DAYS_TO_SCAN) return
        val sittings = withContext(Dispatchers.IO) { mealRepository.getUnmergedSince(USER_ID, from) }.toSittings()
        val boluses = withContext(Dispatchers.IO) { bolusRepository.getBolusBetween(from, now, USER_ID) }
        val events = ActivityEvents.build(withContext(Dispatchers.IO) { activityRepository.getBetween(from, now, USER_ID) })
        val input = DetectionInput(days, getTargetRange().toCore().toThresholds(), now, zone, sittings, boluses, events)

        val stored = store.getAll()
        val candidates = TimeOfDayDetector.detect(input) + MealDetector.detect(input) + ExerciseDetector.detect(input)
        val cards = InsightPipeline.finalize(
            candidates,
            existingTips = stored.associate { it.card.id to it.card.tips },
            tipsInUse = stored.filter { it.state != InsightState.RESOLVED }.flatMap { it.card.tips }.toSet()
        )
        val result = InsightLifecycle.reconcile(stored, cards, now)
        store.replaceAll(result.insights)
        if (result.toNotify.isNotEmpty()) {
            val single = result.toNotify.singleOrNull()
            val title = if (single != null) "New pattern" else "${result.toNotify.size} new patterns"
            val text = result.toNotify.joinToString("\n") { it.card.title }
            notifier.pushOnly(CHANNEL, "insights-${result.toNotify.map { it.card.id }.sorted().hashCode()}", title, text, single?.let { insightLink(it.card.id) } ?: INSIGHTS_LINK)
            store.markNotified(result.toNotify.map { it.card.id }, now)
        }
        Log.i(TAG, "${candidates.size} candidates, ${cards.size} surfaced, ${result.toNotify.size} notified over ${days.size} days")
    }

    private companion object {
        const val TAG = "Insights"
        const val USER_ID = 1
        const val MIN_DAYS_TO_SCAN = 7
        val RUN_LOCK = Mutex()
        val CHANNEL = NotificationChannelSpec(
            "GLUCOSE_INSIGHTS_CHANNEL", "Glucose patterns",
            "New patterns found in your glucose data", NotificationManager.IMPORTANCE_DEFAULT
        )
    }
}

/** Schedules [InsightWorker]: every 12 hours, plus a scan when the app opens. */
class InsightScheduler(private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            "insights_periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<InsightWorker>(12, TimeUnit.HOURS).build()
        )
    }

    fun checkNow() {
        workManager.enqueueUniqueWork("insights_now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<InsightWorker>().build())
    }
}
