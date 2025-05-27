package uk.scimone.diafit.sync.di

import org.koin.dsl.module
import uk.scimone.diafit.core.domain.repository.CgmSyncSource
import uk.scimone.diafit.core.domain.usecase.SyncCgmDataUseCase
import uk.scimone.diafit.core.data.repository.NightscoutCgmSyncSource

val syncModule = module {

    // Bind Nightscout sync source to interface
    single<CgmSyncSource> { NightscoutCgmSyncSource(get(), get()) }

    // UseCase that triggers all available sync sources
    single {
        SyncCgmDataUseCase(syncSources = listOf(get()))
    }
}
