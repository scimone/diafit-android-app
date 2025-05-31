package uk.scimone.diafit.addmeal.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.addmeal.presentation.AddMealViewModel

val addmealModule = module {
    viewModel { (userId: Int) ->
        AddMealViewModel(
            mealRepository = get(),
            fileStorageRepository = get(),
            userId = userId,
            application = get()
        )
    }
}