package uk.scimone.diafit.core.di

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import io.ktor.client.engine.android.Android
import org.koin.core.qualifier.named
import org.koin.dsl.module
import uk.scimone.diafit.core.data.file.FileStorageRepositoryImpl
import uk.scimone.diafit.core.data.repository.MealRepositoryImpl
import uk.scimone.diafit.core.data.local.AppDatabase
import uk.scimone.diafit.core.data.local.CgmDao
import uk.scimone.diafit.core.data.local.MealDao
import uk.scimone.diafit.core.data.networking.util.HttpClientFactory
import uk.scimone.diafit.core.data.networking.NightscoutApi
import uk.scimone.diafit.core.data.repository.CgmRepositoryImpl
import uk.scimone.diafit.core.domain.repository.CgmRepository
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.usecase.CreateMealUseCase
import uk.scimone.diafit.core.domain.usecase.GetAllCgmSinceUseCase
import uk.scimone.diafit.core.domain.usecase.GetLatestCgmUseCase
import uk.scimone.diafit.core.domain.usecase.SyncCgmDataUseCase

val coreModule = module {

    // Provide Room database singleton
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "diafit_database"
        )
        .fallbackToDestructiveMigration(false)
        .build()
    }

    // Provide DAO from database
    single<MealDao> { get<AppDatabase>().mealDao() }
    single<CgmDao> { get<AppDatabase>().cgmDao() }

    // Provide file storage
    single<FileStorageRepository> { FileStorageRepositoryImpl(get()) }

    // Provide meal repository and use cases
    single<MealRepository> { MealRepositoryImpl(get(), get()) }
    single { CreateMealUseCase(get()) }

    // Provide CGM repository and use cases
    single<CgmRepository> { CgmRepositoryImpl(get()) }
    single { GetLatestCgmUseCase(get()) }
    single { GetAllCgmSinceUseCase(get()) }

    // Provide sync use cases
    single {
        SyncCgmDataUseCase(
            getCgmSourceUseCase = get(),
            nightscoutSource = get(named("NIGHTSCOUT")),
            xdripSource = get(named("XDRIP")),
            jugglucoSource = get(named("JUGGLUCO")),
            mockSource = get(named("MOCK"))
        )
    }

    // Provide Nightscout API
    single { HttpClientFactory.create(Android.create()) }
    single { NightscoutApi(get()) }
}
