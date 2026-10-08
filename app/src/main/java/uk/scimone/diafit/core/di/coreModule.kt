package uk.scimone.diafit.core.di

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import io.ktor.client.engine.android.Android
import org.koin.core.qualifier.named
import org.koin.dsl.module
import uk.scimone.diafit.core.data.file.FileStorageRepositoryImpl
import uk.scimone.diafit.core.data.repository.MealAnalysisRepositoryImpl
import uk.scimone.diafit.core.data.repository.MealRepositoryImpl
import uk.scimone.diafit.core.data.local.ActivityDao
import uk.scimone.diafit.core.data.local.AppDatabase
import uk.scimone.diafit.core.data.repository.ActivityRepositoryImpl
import uk.scimone.diafit.core.domain.repository.ActivityRepository
import uk.scimone.diafit.core.domain.usecase.GetActivityBetweenUseCase
import uk.scimone.diafit.core.domain.usecase.ObserveActivitySinceUseCase
import uk.scimone.diafit.core.data.local.BolusDao
import uk.scimone.diafit.core.data.local.CgmDao
import uk.scimone.diafit.core.data.local.MealDao
import uk.scimone.diafit.core.data.local.PumpEventDao
import uk.scimone.diafit.core.data.repository.PumpEventRepositoryImpl
import uk.scimone.diafit.core.domain.repository.PumpEventRepository
import uk.scimone.diafit.core.data.networking.util.HttpClientFactory
import uk.scimone.diafit.core.data.networking.NightscoutApi
import uk.scimone.diafit.core.data.networking.OpenAiApi
import uk.scimone.diafit.core.data.repository.BolusRepositoryImpl
import uk.scimone.diafit.core.data.repository.CgmRepositoryImpl
import uk.scimone.diafit.core.domain.repository.BolusRepository
import uk.scimone.diafit.core.domain.repository.CgmRepository
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import uk.scimone.diafit.core.domain.repository.MealAnalysisRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.usecase.AnalyzeMealUseCase
import uk.scimone.diafit.core.domain.usecase.CalculateMealGlucoseImpactUseCase
import uk.scimone.diafit.core.domain.usecase.GetMealOutcomeUseCase
import uk.scimone.diafit.core.domain.usecase.CreateMealUseCase
import uk.scimone.diafit.core.domain.usecase.MergeCarbEntriesUseCase
import uk.scimone.diafit.core.domain.usecase.GetGlucoseResponseUseCase
import uk.scimone.diafit.core.domain.usecase.GetMealSittingUseCase
import uk.scimone.diafit.core.domain.usecase.GetOpenSittingUseCase
import uk.scimone.diafit.core.domain.usecase.SetMealValidUseCase
import uk.scimone.diafit.core.domain.usecase.UpdateMealUseCase
import uk.scimone.diafit.core.domain.usecase.GetAllBolusSinceUseCase
import uk.scimone.diafit.core.domain.usecase.GetAllCgmSinceUseCase
import uk.scimone.diafit.core.domain.usecase.GetAllMealsSinceUseCase
import uk.scimone.diafit.core.domain.usecase.GetLatestCgmUseCase
import uk.scimone.diafit.core.domain.usecase.InsertBolusUseCase
import uk.scimone.diafit.core.domain.usecase.InsertCgmUseCase

val coreModule = module {

    // Provide Room database singleton
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "diafit_database"
        )
            .addMigrations(AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14, AppDatabase.MIGRATION_14_15, AppDatabase.MIGRATION_15_16, AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19, AppDatabase.MIGRATION_19_20)
            .build()
    }

    // Provide DAO from database
    single<MealDao> { get<AppDatabase>().mealDao() }
    single<CgmDao> { get<AppDatabase>().cgmDao() }
    single<BolusDao> { get<AppDatabase>().bolusDao() }
    single<PumpEventDao> { get<AppDatabase>().pumpEventDao() }
    single<ActivityDao> { get<AppDatabase>().activityDao() }
    single<uk.scimone.diafit.core.data.local.NotificationDao> { get<AppDatabase>().notificationDao() }
    single { uk.scimone.diafit.core.data.worker.DeviceExpiryScheduler(androidContext()) }
    single<ActivityRepository> { ActivityRepositoryImpl(get()) }
    single { ObserveActivitySinceUseCase(get()) }
    single { GetActivityBetweenUseCase(get()) }
    single<PumpEventRepository> { PumpEventRepositoryImpl(get()) }
    single { uk.scimone.diafit.core.data.repository.PumpEventRepair(get(), androidContext()) }

    // Provide file storage
    single<FileStorageRepository> { FileStorageRepositoryImpl(get()) }

    // Provide meal repository and use cases
    single<MealRepository> { MealRepositoryImpl(get()) }
    single { MergeCarbEntriesUseCase(get()) }
    single { CreateMealUseCase(get(), get(), get()) }
    single { UpdateMealUseCase(get(), get(), get()) }
    single { SetMealValidUseCase(get()) }
    single { CalculateMealGlucoseImpactUseCase(get(), get()) }
    single { GetMealOutcomeUseCase(get(), get()) }
    single { GetGlucoseResponseUseCase(get(), get()) }
    single { GetAllMealsSinceUseCase(get()) }
    single { GetMealSittingUseCase(get()) }
    single { GetOpenSittingUseCase(get(), get()) }

    // Provide CGM repository and use cases
    single<CgmRepository> { CgmRepositoryImpl(get()) }
    single { GetLatestCgmUseCase(get()) }
    single { GetAllCgmSinceUseCase(get()) }
    single { InsertCgmUseCase(get()) }

    // Provide bolus repositories and use cases
    single<BolusRepository> { BolusRepositoryImpl(get()) }
    single { InsertBolusUseCase(get()) }
    single { GetAllBolusSinceUseCase(get()) }
    single { uk.scimone.diafit.core.domain.usecase.GetBasalTimelineUseCase(get()) }

    // Nightscout HTTP API
    single { HttpClientFactory.create(Android.create()) }
    single { NightscoutApi(get(), get()) }

    // OpenAI-compatible meal photo analysis
    single { OpenAiApi(get()) }
    single<MealAnalysisRepository> { MealAnalysisRepositoryImpl(get(), get(), get()) }
    single { AnalyzeMealUseCase(get()) }
}
