package uk.scimone.diafit

import uk.scimone.diafit.core.presentation.AppStrings
import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import uk.scimone.diafit.core.data.worker.CgmServiceWatchdogWorker
import uk.scimone.diafit.core.di.coreModule
import uk.scimone.diafit.addmeal.di.addmealModule
import uk.scimone.diafit.history.di.historyModule
import uk.scimone.diafit.home.di.homeModule
import uk.scimone.diafit.logs.di.logsModule
import uk.scimone.diafit.settings.di.settingsModule
import uk.scimone.diafit.profile.di.profileModule
import uk.scimone.diafit.patterns.di.patternsModule
import uk.scimone.diafit.core.di.syncModule
import uk.scimone.diafit.backendsync.BackendSyncFeature
import uk.scimone.diafit.backendsync.data.BackendSyncScheduler
import uk.scimone.diafit.backendsync.di.backendSyncModule
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class DiafitApp : Application() {
    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(uk.scimone.diafit.settings.domain.model.AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppStrings.init(this)

        startKoin {
            androidContext(this@DiafitApp)
            modules(
                listOf(
                    coreModule,
                    logsModule,
                    addmealModule,
                    uk.scimone.diafit.recipes.di.recipesModule,
                    homeModule,
                    historyModule,
                    syncModule,
                    settingsModule,
                    profileModule,
                    patternsModule,
                    uk.scimone.diafit.mealactivity.di.mealActivityModule,
                    uk.scimone.diafit.stats.di.statsModule
                ) + (if (BackendSyncFeature.ENABLED) listOf(backendSyncModule) else emptyList()) +
                    (if (uk.scimone.diafit.dailyrecap.DailyRecapFeature.ENABLED) listOf(uk.scimone.diafit.dailyrecap.di.dailyRecapModule) else emptyList())
            )
        }

        // Minimum PeriodicWorkRequest interval is 15 minutes. KEEP means re-launching the
        // app (which re-runs this onCreate) won't duplicate or reset the schedule.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CgmServiceWatchdogWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CgmServiceWatchdogWorker>(15, TimeUnit.MINUTES).build()
        )
        // Sensor / pump-part expiry alerts: survives process death like the watchdog.
        org.koin.java.KoinJavaComponent.get<uk.scimone.diafit.core.data.worker.DeviceExpiryScheduler>(uk.scimone.diafit.core.data.worker.DeviceExpiryScheduler::class.java).schedulePeriodic()
        // Glucose patterns (recurring patterns), twice a day.
        org.koin.java.KoinJavaComponent.get<uk.scimone.diafit.patterns.data.PatternScheduler>(uk.scimone.diafit.patterns.data.PatternScheduler::class.java).schedulePeriodic()
        // Meal activity curves: load the active personal model, watch meals/CGM for changes, and reconcile from persisted data.
        org.koin.java.KoinJavaComponent.get<uk.scimone.diafit.mealactivity.data.MealActivityInvalidationObserver>(uk.scimone.diafit.mealactivity.data.MealActivityInvalidationObserver::class.java).start(appScope)
        org.koin.java.KoinJavaComponent.get<uk.scimone.diafit.mealactivity.data.MealActivityScheduler>(uk.scimone.diafit.mealactivity.data.MealActivityScheduler::class.java).also { it.schedulePeriodic(); it.checkNow() }
        appScope.launch { runCatching { org.koin.java.KoinJavaComponent.get<uk.scimone.diafit.mealactivity.data.MealActivityService>(uk.scimone.diafit.mealactivity.data.MealActivityService::class.java).loadActive(uk.scimone.diafit.mealactivity.data.MealActivityScheduler.USER_ID) } }
        // Optional upload to the user's Diafit backend; the worker is a no-op until one is configured.
        if (BackendSyncFeature.ENABLED) {
            org.koin.java.KoinJavaComponent.get<BackendSyncScheduler>(BackendSyncScheduler::class.java).schedulePeriodic()
        }
        // Optional LLM morning recap: keeps the next 07:00 job queued, but only for people who switched it on.
        if (uk.scimone.diafit.dailyrecap.DailyRecapFeature.ENABLED &&
            org.koin.java.KoinJavaComponent.get<uk.scimone.diafit.dailyrecap.data.DailyRecapStore>(uk.scimone.diafit.dailyrecap.data.DailyRecapStore::class.java).config.value.enabled
        ) {
            org.koin.java.KoinJavaComponent.get<uk.scimone.diafit.dailyrecap.data.DailyRecapScheduler>(uk.scimone.diafit.dailyrecap.data.DailyRecapScheduler::class.java).scheduleNext()
        }
    }

}
