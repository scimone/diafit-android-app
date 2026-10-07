package uk.scimone.diafit.home.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.home.presentation.HomeViewModel

val homeModule = module {

    viewModel { (userId: Int) ->
        HomeViewModel(
            getLatestCgmUseCase = get(),
            getAllCgmSinceUseCase = get(),
            getTargetRangeUseCase = get(),
            observeActivitySinceUseCase = get(),
            getAllMealsSinceUseCase = get(),
            getAllBolusSinceUseCase = get(),
            application = get(),
            mealRepository = get(),
            fileStorageRepository = get(),
            userId = userId
        )
    }
}