package uk.scimone.diafit

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import uk.scimone.diafit.core.di.coreModule
import uk.scimone.diafit.addmeal.di.addmealModule
import uk.scimone.diafit.home.di.homeModule
import uk.scimone.diafit.journal.di.journalModule
import uk.scimone.diafit.settings.di.settingsModule
import uk.scimone.diafit.core.di.syncModule

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
                    syncModule,
                    settingsModule
                )
            )
        }

    }

}
