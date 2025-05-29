package uk.scimone.diafit

import android.app.Application
import androidx.work.Configuration
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import uk.scimone.diafit.di.coreModule
import uk.scimone.diafit.di.addmealModule
import uk.scimone.diafit.di.homeModule
import uk.scimone.diafit.di.journalModule
import uk.scimone.diafit.di.syncModule

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
                    syncModule
                )
            )
        }

    }

}
