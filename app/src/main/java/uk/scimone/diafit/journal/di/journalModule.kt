package uk.scimone.diafit.journal.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.journal.presentation.JournalViewModel

val journalModule = module {
    viewModel {
        JournalViewModel(
            mealRepository = get(),
            calculateMealGlucoseImpactUseCase = get(),
            getTargetRangeUseCase = get(),
            context = get(),
            userId = get()
        )
    }

}
