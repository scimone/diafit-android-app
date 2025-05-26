package uk.scimone.diafit.di

import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.addmeal.presentation.AddMealViewModel

val addmealModule = module {
    viewModel { (userId: String) ->
        AddMealViewModel(
            mealRepository = get(),
            fileStorageRepository = get(),
            userId = userId,
            application = get()
        )
    }
}