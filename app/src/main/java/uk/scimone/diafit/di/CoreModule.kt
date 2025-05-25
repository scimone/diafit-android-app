package uk.scimone.diafit.core.di

import android.content.Context
import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import uk.scimone.diafit.core.data.repository.MealRepositoryImpl
import uk.scimone.diafit.core.data.local.AppDatabase
import uk.scimone.diafit.core.data.local.MealDao
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.usecase.CreateMealUseCase

val coreModule = module {

    // Provide Room database singleton
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "diafit_database"
        ).build()
    }

    // Provide DAO from database
    single<MealDao> { get<AppDatabase>().mealDao() }

    // Provide repository implementation
    single<MealRepository> { MealRepositoryImpl(get(), get()) }  // Provide MealRepositoryImpl
    single { CreateMealUseCase(get()) }

    // Provide CreateMealUseCase
}
