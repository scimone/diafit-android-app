package uk.scimone.diafit.core.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource.CgmSyncSourceJuggluco
import uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource.CgmSyncSourceNightscout
import uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource.CgmSyncSourceXdrip
import uk.scimone.diafit.core.data.service.CgmServiceManager
import uk.scimone.diafit.core.data.worker.CgmRemotePoller
import uk.scimone.diafit.core.domain.repository.syncsource.CgmSyncSource
import uk.scimone.diafit.core.domain.repository.syncsource.IntentCgmSyncSource
import uk.scimone.diafit.core.domain.usecase.SyncCgmDataUseCase
import uk.scimone.diafit.settings.domain.model.CgmSource

val syncModule = module {
    single<CgmSyncSource>(named("NIGHTSCOUT")) { CgmSyncSourceNightscout(get(), get()) }
    factory<CgmSyncSource>(named("JUGGLUCO")) { CgmSyncSourceJuggluco() }
    factory<IntentCgmSyncSource>(named("JUGGLUCO")) { get<CgmSyncSource>(named("JUGGLUCO")) as IntentCgmSyncSource }

    factory<CgmSyncSource>(named("XDRIP")) { CgmSyncSourceXdrip() }
    factory<IntentCgmSyncSource>(named("XDRIP")) { get<CgmSyncSource>(named("XDRIP")) as IntentCgmSyncSource }


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

    single { CgmServiceManager(androidContext()) }

    factory {
        CgmRemotePoller(get()) // no qualifier, gets unqualified SyncCgmDataUseCase
    }
}

