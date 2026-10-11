package uk.scimone.diafit.patterns.di

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.patterns.data.PatternScheduler
import uk.scimone.diafit.patterns.data.PatternStore
import uk.scimone.diafit.patterns.presentation.PatternDetailViewModel
import uk.scimone.diafit.patterns.presentation.PatternsViewModel
import uk.scimone.diafit.notifications.data.AppNotifier

val patternsModule = module {
    single { AppNotifier(androidContext(), get()) }
    single { PatternStore(get<uk.scimone.diafit.core.data.local.AppDatabase>().patternDao()) }
    single { PatternScheduler(androidContext()) }
    viewModel { PatternsViewModel(get()) }
    viewModel { (id: String) -> PatternDetailViewModel(get(), get(), id) }
}
