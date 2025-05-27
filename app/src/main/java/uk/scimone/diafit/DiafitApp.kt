package uk.scimone.diafit

import android.app.Application
import org.koin.core.context.startKoin
import org.koin.android.ext.koin.androidContext
import uk.scimone.diafit.di.coreModule
import uk.scimone.diafit.di.addmealModule
import uk.scimone.diafit.di.journalModule
import uk.scimone.diafit.sync.di.syncModule

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
                    syncModule
                )
            )
        }
    }
}
