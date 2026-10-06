package uk.scimone.diafit.history.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.history.domain.usecase.ClusterTreatmentsUseCase
import uk.scimone.diafit.history.domain.usecase.GetDailyHistoryUseCase
import uk.scimone.diafit.history.presentation.HistoryViewModel

val historyModule = module {
    single { ClusterTreatmentsUseCase() }
    single { GetDailyHistoryUseCase(get(), get(), get()) }

    viewModel { (userId: Int) ->
        HistoryViewModel(
            getDailyHistory = get(),
            clusterTreatments = get(),
            getTargetRange = get(),
            userId = userId
        )
    }
}
