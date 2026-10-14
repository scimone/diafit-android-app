package uk.scimone.diafit.dailyrecap.di

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.dailyrecap.data.DailyRecapScheduler
import uk.scimone.diafit.dailyrecap.data.DailyRecapService
import uk.scimone.diafit.dailyrecap.data.DailyRecapStore
import uk.scimone.diafit.dailyrecap.data.RecapDataLoader
import uk.scimone.diafit.dailyrecap.data.RecapLlm
import uk.scimone.diafit.dailyrecap.data.RecapNotifier
import uk.scimone.diafit.dailyrecap.presentation.DailyRecapViewModel

val dailyRecapModule = module {
    single { DailyRecapStore(androidContext()) }
    single { DailyRecapScheduler(androidContext(), get()) }
    single { RecapNotifier(androidContext()) }
    single { RecapLlm(get(), get()) }
    single { RecapDataLoader(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { DailyRecapService(get(), get(), get(), get(), get(), get()) }
    viewModel { DailyRecapViewModel(get(), get(), get(), get()) }
}
