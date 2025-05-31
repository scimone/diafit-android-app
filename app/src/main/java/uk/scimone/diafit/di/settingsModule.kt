package uk.scimone.diafit.di

import org.koin.dsl.module
import uk.scimone.diafit.settings.data.repository.SettingsRepositoryImpl
import uk.scimone.diafit.settings.domain.repository.SettingsRepository
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase
import uk.scimone.diafit.settings.domain.usecase.SetCgmSourceUseCase
import uk.scimone.diafit.settings.presentation.SettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel

val settingsModule = module {

    single<SettingsRepository> {
        SettingsRepositoryImpl(context = get())
    }

    factory { GetCgmSourceUseCase(get()) }
    factory { SetCgmSourceUseCase(get()) }

    viewModel {
        SettingsViewModel(
            getCgmSource = get(),
            setCgmSource = get()
        )
    }
}
