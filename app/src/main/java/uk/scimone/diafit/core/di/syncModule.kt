package uk.scimone.diafit.core.di

import org.koin.core.qualifier.named
import org.koin.dsl.module
import uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource.CgmSyncSourceJuggluco
import uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource.CgmSyncSourceMockData
import uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource.CgmSyncSourceNightscout
import uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource.CgmSyncSourceXdrip
import uk.scimone.diafit.core.data.worker.CgmPoller
import uk.scimone.diafit.core.domain.repository.syncsource.CgmSyncSource

val syncModule = module {
    single<CgmSyncSource>(named("NIGHTSCOUT")) { CgmSyncSourceNightscout(get(), get()) }
    single<CgmSyncSource>(named("XDRIP")) { CgmSyncSourceXdrip() }
    single<CgmSyncSource>(named("JUGGLUCO")) { CgmSyncSourceJuggluco() }
    single<CgmSyncSource>(named("MOCK")) { CgmSyncSourceMockData() }

    // Provide all sources separately
    single<List<CgmSyncSource>> {
        listOf(
            get(named("NIGHTSCOUT")),
            get(named("XDRIP")),
            get(named("JUGGLUCO")),
            get(named("MOCK"))
        )
    }

    single { CgmPoller(get()) }
}
