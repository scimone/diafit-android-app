package uk.scimone.diafit.patterns.di

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.notifications.data.AppNotifier
import uk.scimone.diafit.patterns.data.PatternAlertStore
import uk.scimone.diafit.patterns.data.PatternScheduler
import uk.scimone.diafit.patterns.domain.GetGlucosePatternsUseCase
import uk.scimone.diafit.patterns.presentation.PatternsViewModel

val patternsModule = module {
    single { AppNotifier(androidContext(), get()) }
    single { GetGlucosePatternsUseCase(get()) }
    single { PatternAlertStore(androidContext()) }
    single { PatternScheduler(androidContext()) }
    viewModel { (userId: Int, highlighted: List<String>) -> PatternsViewModel(get(), get(), userId, highlighted) }
}
