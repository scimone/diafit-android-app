package uk.scimone.diafit.backendsync.di

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.backendsync.data.BackendApi
import uk.scimone.diafit.backendsync.data.BackendSyncScheduler
import uk.scimone.diafit.backendsync.data.BackendSyncStore
import uk.scimone.diafit.backendsync.data.BackendSyncer
import uk.scimone.diafit.backendsync.presentation.BackendSyncViewModel
import uk.scimone.diafit.core.data.local.AppDatabase

val backendSyncModule = module {
    single { BackendApi() }
    single { BackendSyncStore(androidContext()) }
    single { BackendSyncScheduler(androidContext()) }
    single { get<AppDatabase>().backendSyncDao() }
    single { BackendSyncer(get(), get(), get()) }
    viewModel { BackendSyncViewModel(get(), get(), get()) }
}
