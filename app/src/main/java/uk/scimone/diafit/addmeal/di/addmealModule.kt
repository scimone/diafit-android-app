package uk.scimone.diafit.addmeal.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.addmeal.presentation.AddMealViewModel

val addmealModule = module {
    viewModel { (userId: Int) ->
        AddMealViewModel(
            createMealUseCase = get(),
            updateMealUseCase = get(),
            setMealValidUseCase = get(),
            getMealSitting = get(),
            mealRepository = get(),
            bolusRepository = get(),
            analyzeMealUseCase = get(),
            lookupBarcode = get(),
            searchFoodByName = get(),
            savedProducts = get(),
            fileStorageRepository = get(),
            settingsRepository = get(),
            userId = userId,
            application = get()
        )
    }
}