package uk.scimone.diafit.history.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.history.domain.usecase.ClusterTreatmentsUseCase
import uk.scimone.diafit.history.domain.usecase.GetDailyHistoryUseCase
import uk.scimone.diafit.history.domain.usecase.GetDayDetailUseCase
import uk.scimone.diafit.history.presentation.HistoryViewModel
import uk.scimone.diafit.history.presentation.detail.DayDetailViewModel

val historyModule = module {
    single { ClusterTreatmentsUseCase() }
    single { GetDailyHistoryUseCase(get(), get(), get()) }
    single { GetDayDetailUseCase(get(), get(), get()) }

    viewModel { (userId: Int) ->
        HistoryViewModel(
            getDailyHistory = get(),
            clusterTreatments = get(),
            getTargetRange = get(),
            userId = userId
        )
    }

    viewModel { (userId: Int, epochDay: Long) ->
        DayDetailViewModel(
            getDayDetail = get(),
            getTargetRange = get(),
            context = get(),
            userId = userId,
            epochDay = epochDay
        )
    }
}
