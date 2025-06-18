package uk.scimone.diafit.core.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource.BolusSyncSourceAaps
import uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource.CgmSyncSourceJuggluco
import uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource.CgmSyncSourceNightscout
import uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource.CgmSyncSourceXdrip
import uk.scimone.diafit.core.data.service.CgmServiceManager
import uk.scimone.diafit.core.data.worker.CgmRemotePoller
import uk.scimone.diafit.core.domain.repository.syncsource.HealthSyncSource
import uk.scimone.diafit.core.domain.repository.syncsource.IntentHealthSyncSource
import uk.scimone.diafit.core.domain.usecase.SyncBolusDataUseCase
import uk.scimone.diafit.core.domain.usecase.SyncCgmDataUseCase
import uk.scimone.diafit.settings.domain.model.BolusSource
import uk.scimone.diafit.settings.domain.model.CgmSource

val syncModule = module {
    single<HealthSyncSource>(named("NIGHTSCOUT")) { CgmSyncSourceNightscout(get(), get()) }
    factory<HealthSyncSource>(named("JUGGLUCO")) { CgmSyncSourceJuggluco() }
    factory<IntentHealthSyncSource>(named("JUGGLUCO")) { get<HealthSyncSource>(named("JUGGLUCO")) as IntentHealthSyncSource }

    factory<HealthSyncSource>(named("XDRIP")) { CgmSyncSourceXdrip() }
    factory<IntentHealthSyncSource>(named("XDRIP")) { get<HealthSyncSource>(named("XDRIP")) as IntentHealthSyncSource }

    factory<HealthSyncSource>(named("AAPS")) { BolusSyncSourceAaps() }
    factory<IntentHealthSyncSource>(named("AAPS")) { get<HealthSyncSource>(named("AAPS")) as IntentHealthSyncSource }

    // Single SyncCgmDataUseCase with all sources
    single {
        SyncCgmDataUseCase(
            getCgmSourceUseCase = get(),
            sources = mapOf(
                CgmSource.NIGHTSCOUT to get(named("NIGHTSCOUT")),
                CgmSource.XDRIP to get(named("XDRIP")),
                CgmSource.JUGGLUCO to get(named("JUGGLUCO"))
            )
        )
    }

    // Single SyncBolusDataUseCase with all sources
    single {
        SyncBolusDataUseCase(
            getBolusSourceUseCase = get(),
            sources = mapOf(
                BolusSource.AAPS to get(named("AAPS")),
            )
        )
    }

    single { CgmServiceManager(androidContext()) }

    factory {
        CgmRemotePoller(get()) // no qualifier, gets unqualified SyncCgmDataUseCase
    }
}

