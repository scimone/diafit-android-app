package uk.scimone.diafit.profile.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.profile.presentation.ProfileViewModel

val profileModule = module {
    viewModel { (userId: Int) -> ProfileViewModel(pumpEventRepository = get(), userId = userId) }
}
