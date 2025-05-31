package uk.scimone.diafit.core.di

import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource.CgmSyncSourceJuggluco
import uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource.CgmSyncSourceMockData
import uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource.CgmSyncSourceNightscout
import uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource.CgmSyncSourceXdrip
import uk.scimone.diafit.core.data.worker.CgmPoller
import uk.scimone.diafit.core.domain.repository.syncsource.CgmSyncSource
import uk.scimone.diafit.core.domain.repository.syncsource.IntentCgmSyncSource
import uk.scimone.diafit.core.domain.usecase.SyncCgmDataUseCase
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase

val syncModule = module {
    single<CgmSyncSource>(named("NIGHTSCOUT")) { CgmSyncSourceNightscout(get(), get()) }
    single<CgmSyncSource>(named("XDRIP")) { CgmSyncSourceXdrip() }
    single<CgmSyncSource>(named("MOCK")) { CgmSyncSourceMockData() }
    factory<IntentCgmSyncSource>(named("JUGGLUCO")) { CgmSyncSourceJuggluco(get()) }

    // Single SyncCgmDataUseCase with all sources
    single {
        SyncCgmDataUseCase(
            getCgmSourceUseCase = get(),
            sources = mapOf(
                CgmSource.NIGHTSCOUT to get(named("NIGHTSCOUT")),
                CgmSource.XDRIP to get(named("XDRIP")),
                CgmSource.MOCK to get(named("MOCK"))
            )
        )
    }

    factory {
        CgmPoller(get()) // no qualifier, gets unqualified SyncCgmDataUseCase
    }
}

