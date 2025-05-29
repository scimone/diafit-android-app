package uk.scimone.diafit.di

import org.koin.dsl.module
import uk.scimone.diafit.core.data.worker.CgmPoller
import uk.scimone.diafit.core.domain.repository.CgmSyncSource
import uk.scimone.diafit.core.data.repository.NightscoutCgmSyncSource

val syncModule = module {
    single<CgmSyncSource> { NightscoutCgmSyncSource(get(), get()) }
    single<List<CgmSyncSource>> { listOf(get()) }
    single { CgmPoller(get()) }
}
