package uk.scimone.diafit.insights.di

import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.insights.data.InsightScheduler
import uk.scimone.diafit.insights.data.InsightStore
import uk.scimone.diafit.insights.presentation.InsightDetailViewModel
import uk.scimone.diafit.insights.presentation.InsightsViewModel
import uk.scimone.diafit.notifications.data.AppNotifier

val insightsModule = module {
    single { AppNotifier(androidContext(), get()) }
    single { InsightStore(get<uk.scimone.diafit.core.data.local.AppDatabase>().insightDao()) }
    single { InsightScheduler(androidContext()) }
    viewModel { InsightsViewModel(get(), get()) }
    viewModel { (id: String) -> InsightDetailViewModel(get(), get(), id) }
}
