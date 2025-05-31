package uk.scimone.diafit.di

import org.koin.core.qualifier.named
import org.koin.dsl.module
import uk.scimone.diafit.core.data.repository.*
import uk.scimone.diafit.core.data.worker.CgmPoller
import uk.scimone.diafit.core.domain.repository.CgmSyncSource

val syncModule = module {
    single<CgmSyncSource>(named("NIGHTSCOUT")) { NightscoutCgmSyncSource(get(), get()) }
    single<CgmSyncSource>(named("XDRIP")) { XdripCgmSyncSource() }
    single<CgmSyncSource>(named("JUGGLUCO")) { JugglucoCgmSyncSource() }
    single<CgmSyncSource>(named("MOCK")) { MockDataCgmSyncSource() }

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
