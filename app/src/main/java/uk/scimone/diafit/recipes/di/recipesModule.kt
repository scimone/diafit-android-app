package uk.scimone.diafit.recipes.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.recipes.presentation.RecipeEditorViewModel
import uk.scimone.diafit.recipes.presentation.RecipesViewModel

val recipesModule = module {
    viewModel { RecipesViewModel(get()) }
    viewModel { RecipeEditorViewModel(get()) }
}
