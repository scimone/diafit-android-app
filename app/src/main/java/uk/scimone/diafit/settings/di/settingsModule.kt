package uk.scimone.diafit.settings.di

import kotlinx.coroutines.flow.StateFlow
import org.koin.dsl.module
import uk.scimone.diafit.settings.data.repository.SettingsRepositoryImpl
import uk.scimone.diafit.settings.domain.repository.SettingsRepository
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase
import uk.scimone.diafit.settings.domain.usecase.SetCgmSourceUseCase
import uk.scimone.diafit.settings.presentation.SettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.settings.domain.usecase.GetBolusSourceUseCase
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.domain.usecase.SetBolusSourceUseCase
import uk.scimone.diafit.settings.domain.usecase.SetTargetRangeUseCase

val settingsModule = module {

    single<SettingsRepository> {
        SettingsRepositoryImpl(context = get())
    }

    single { GetCgmSourceUseCase(get()) }
    single { SetCgmSourceUseCase(get()) }
    single { GetBolusSourceUseCase(get())}
    single { SetBolusSourceUseCase(get()) }
    single { GetTargetRangeUseCase(get()) }
    single { SetTargetRangeUseCase(get()) }

    viewModel {
        SettingsViewModel(
            getCgmSource = get(),
            setCgmSource = get(),
            getBolusSource = get(),
            setBolusSource = get(),
            getGlucoseTargetRange = get(),
            setGlucoseTargetRange = get(),
            appContext = get()
        )
    }
}
