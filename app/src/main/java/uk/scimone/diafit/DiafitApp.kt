package uk.scimone.diafit

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
import uk.scimone.diafit.journal.di.journalModule
import uk.scimone.diafit.settings.di.settingsModule
import uk.scimone.diafit.profile.di.profileModule
import uk.scimone.diafit.core.di.syncModule
import java.util.concurrent.TimeUnit

class DiafitApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@DiafitApp)
            modules(
                listOf(
                    coreModule,
                    journalModule,
                    addmealModule,
                    homeModule,
                    historyModule,
                    syncModule,
                    settingsModule,
                    profileModule
                )
            )
        }

        // Minimum PeriodicWorkRequest interval is 15 minutes. KEEP means re-launching the
        // app (which re-runs this onCreate) won't duplicate or reset the schedule.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CgmServiceWatchdogWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CgmServiceWatchdogWorker>(15, TimeUnit.MINUTES).build()
        )
    }

}
