package uk.scimone.diafit.journal.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.journal.presentation.JournalViewModel
import uk.scimone.diafit.journal.presentation.detail.MealDetailViewModel

val journalModule = module {
    viewModel {
        JournalViewModel(
            mealRepository = get(),
            getMealOutcome = get(),
            cgmRepository = get(),
            bolusRepository = get(),
            pumpEventRepository = get(),
            mergeCarbEntries = get(),
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
