package uk.scimone.diafit.logs.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.logs.presentation.LogsViewModel
import uk.scimone.diafit.logs.presentation.detail.MealDetailViewModel

val logsModule = module {
    viewModel {
        LogsViewModel(
            mealRepository = get(),
            getMealOutcome = get(),
            cgmRepository = get(),
            bolusRepository = get(),
            pumpEventRepository = get(),
            activityRepository = get(),
            mergeCarbEntries = get(),
            updateMeal = get(),
            getTargetRangeUseCase = get(),
            context = get(),
            userId = get()
        )
    }

    viewModel { (userId: Int, mealId: Int) ->
        MealDetailViewModel(
            mealRepository = get(),
            getGlucoseResponse = get(),
            getTargetRange = get(),
            setMealValid = get(),
            mergeCarbEntries = get(),
            context = get(),
            userId = userId,
            mealId = mealId
        )
    }
}
