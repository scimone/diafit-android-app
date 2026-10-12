package uk.scimone.diafit.mealactivity.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import uk.scimone.diafit.core.data.local.AppDatabase
import uk.scimone.diafit.mealactivity.data.ActivitySource
import uk.scimone.diafit.mealactivity.data.ActivityStore
import uk.scimone.diafit.mealactivity.data.MealActivityInvalidationObserver
import uk.scimone.diafit.mealactivity.data.MealActivityScheduler
import uk.scimone.diafit.mealactivity.data.MealActivityService
import uk.scimone.diafit.mealactivity.data.RoomActivitySource
import uk.scimone.diafit.mealactivity.data.RoomActivityStore
import uk.scimone.diafit.mealactivity.domain.MealActivityRefresher

val mealActivityModule = module {
    single { get<AppDatabase>().mealActivityDao() }
    single<ActivityStore> { RoomActivityStore(get()) }
    single<ActivitySource> { RoomActivitySource(get(), get(), get()) }
    single { MealActivityService(get(), get()) }
    single<MealActivityRefresher> { get<MealActivityService>() }
    single { MealActivityScheduler(androidContext()) }
    single { MealActivityInvalidationObserver(get(), get()) }
}
