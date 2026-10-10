package uk.scimone.diafit.stats.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.stats.data.LoadStatsDaysUseCase
import uk.scimone.diafit.stats.domain.StatsMetric
import uk.scimone.diafit.stats.domain.StatsPeriod
import uk.scimone.diafit.stats.presentation.StatsDetailViewModel
import uk.scimone.diafit.stats.presentation.StatsViewModel

val statsModule = module {
    single { LoadStatsDaysUseCase(get(), get(), get(), get(), get()) }
    viewModel { (userId: Int) -> StatsViewModel(get(), get(), get(), get(), get(), userId) }
    viewModel { (userId: Int, metric: StatsMetric, period: StatsPeriod, offset: Int) ->
        StatsDetailViewModel(get(), get(), userId, metric, period, offset)
    }
}
