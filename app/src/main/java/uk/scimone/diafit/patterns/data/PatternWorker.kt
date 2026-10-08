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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uk.scimone.diafit.notifications.data.AppNotifier
import uk.scimone.diafit.notifications.data.NotificationChannelSpec
import uk.scimone.diafit.notifications.domain.patternsLink
import uk.scimone.diafit.patterns.domain.GetGlucosePatternsUseCase
import uk.scimone.diafit.patterns.domain.newPatternAlerts
import uk.scimone.diafit.patterns.domain.patternAlertMessage
import java.util.concurrent.TimeUnit

/**
 * Recomputes the 14-day pattern AGP and notifies about alert-worthy patterns that weren't there on the previous
 * check (see [newPatternAlerts]). Runs every 12 hours and when the app opens; patterns move slowly, so more often
 * would only repeat the same answer.
 */
class PatternWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val getPatterns: GetGlucosePatternsUseCase by inject()
    private val store: PatternAlertStore by inject()
    private val notifier: AppNotifier by inject()

    override suspend fun doWork(): Result {
        return try {
            // The periodic and the on-open run can start together: one at a time, so the second sees the first's state.
            RUN_LOCK.withLock {
                val now = System.currentTimeMillis()
                val result = getPatterns(USER_ID, now)
                // Not enough data says nothing about which patterns are gone: keep the previous state.
                if (result.agp == null) return Result.success()
                val fresh = newPatternAlerts(result.patterns, store.active(), store.lastAlerted(), now)
                if (fresh.isNotEmpty()) {
                    val (title, text) = patternAlertMessage(fresh)
                    val texts = fresh.map { it.text }
                    val day = java.time.LocalDate.now()
                    notifier.raise(CHANNEL, "patterns-$day-${texts.sorted().hashCode()}", title, text, patternsLink(texts), now)
                    store.markAlerted(texts, now)
                }
                store.setActive(result.patterns.map { it.text }.toSet())
                Log.i(TAG, "${result.patterns.size} patterns over ${result.dayCount} days, ${fresh.size} new")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Pattern check failed", e)
            Result.success() // the next run retries
        }
    }

    private companion object {
        const val TAG = "Patterns"
        const val USER_ID = 1
        val RUN_LOCK = Mutex()
        val CHANNEL = NotificationChannelSpec(
            "GLUCOSE_PATTERNS_CHANNEL", "Glucose patterns",
            "Recurring glucose patterns found in the last two weeks", NotificationManager.IMPORTANCE_DEFAULT
        )
    }
}

/** Schedules [PatternWorker]: every 12 hours, plus a check when the app opens. */
class PatternScheduler(private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            "patterns_periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PatternWorker>(12, TimeUnit.HOURS).build()
        )
    }

    fun checkNow() {
        workManager.enqueueUniqueWork("patterns_now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<PatternWorker>().build())
    }
}

/** Which patterns were found on the last check, and when each was last alerted. */
class PatternAlertStore(context: Context) {
    private val prefs = context.getSharedPreferences("patterns", Context.MODE_PRIVATE)

    fun active(): Set<String> = prefs.getStringSet(KEY_ACTIVE, emptySet()).orEmpty()

    fun setActive(texts: Set<String>) = prefs.edit().putStringSet(KEY_ACTIVE, texts).apply()

    /** Stored as "epochMs|text" entries. */
    fun lastAlerted(): Map<String, Long> = prefs.getStringSet(KEY_ALERTED, emptySet()).orEmpty().mapNotNull { entry ->
        val sep = entry.indexOf('|')
        if (sep < 0) null else entry.substring(sep + 1) to (entry.substring(0, sep).toLongOrNull() ?: return@mapNotNull null)
    }.toMap()

    fun markAlerted(texts: List<String>, nowUtc: Long) {
        val merged = lastAlerted() + texts.associateWith { nowUtc }
        prefs.edit().putStringSet(KEY_ALERTED, merged.map { (t, at) -> "$at|$t" }.toSet()).apply()
    }

    private companion object {
        const val KEY_ACTIVE = "active"
        const val KEY_ALERTED = "alerted"
    }
}
