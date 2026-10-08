package uk.scimone.diafit.profile.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import uk.scimone.diafit.profile.presentation.ProfileSwitchDetailViewModel
import uk.scimone.diafit.profile.presentation.ProfileViewModel

val profileModule = module {
    single { uk.scimone.diafit.devices.presentation.DeviceInputsSource(get(), get()) }
    viewModel { (userId: Int) -> uk.scimone.diafit.devices.presentation.DevicesViewModel(get(), get(), get(), get(), userId) }
    viewModel { (userId: Int) -> ProfileViewModel(pumpEventRepository = get(), userId = userId) }
    viewModel { (userId: Int, eventId: Int) ->
        ProfileSwitchDetailViewModel(repository = get(), userId = userId, eventId = eventId)
    }
}
