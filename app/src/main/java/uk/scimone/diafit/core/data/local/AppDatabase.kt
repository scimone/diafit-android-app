package uk.scimone.diafit.core.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.ExerciseEntity
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.SleepStageEntity
import uk.scimone.diafit.core.domain.model.StepsEntity
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.PumpEventEntity

@Database(
    entities = [
        MealEntity::class, CgmEntity::class, BolusEntity::class, PumpEventEntity::class,
        HeartRateEntity::class, StepsEntity::class, SleepStageEntity::class, ExerciseEntity::class,
        uk.scimone.diafit.core.domain.model.AppNotificationEntity::class,
        uk.scimone.diafit.core.domain.model.HealthMetricEntity::class,
        uk.scimone.diafit.patterns.data.PatternEntity::class,
        uk.scimone.diafit.core.domain.model.SavedProductEntity::class,
        uk.scimone.diafit.mealactivity.data.MealActivityEntity::class,
        uk.scimone.diafit.mealactivity.data.PersonalActivityModelEntity::class,
        uk.scimone.diafit.mealactivity.data.ActivityPipelineStateEntity::class
    ],
    version = 26,
    exportSchema = true,
    // Steps to apply auto-migrations:
    // 1. Make entity changes
    // 2. Bump the version number
    // 3. Comment out the auto-migration block
    // 4. Build the project (to generate the migration files)
    // 5. Uncomment the auto-migration block
    // 6. Build again to apply the migration
//    autoMigrations = [
//        AutoMigration(from = 10, to = 11)
//    ]
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun patternDao(): uk.scimone.diafit.patterns.data.PatternDao
    abstract fun cgmDao(): CgmDao
    abstract fun bolusDao(): BolusDao
    abstract fun pumpEventDao(): PumpEventDao
    abstract fun activityDao(): ActivityDao
    abstract fun notificationDao(): NotificationDao
    abstract fun savedProductDao(): SavedProductDao
    abstract fun mealActivityDao(): uk.scimone.diafit.mealactivity.data.MealActivityDao
    /** Optional backend upload (see BackendSyncFeature); read-only queries, no tables of its own. */
    abstract fun backendSyncDao(): uk.scimone.diafit.backendsync.data.BackendSyncDao

    companion object {
        /** Resting heart rate, HRV and calories from Health Connect (Stats). */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `HealthMetricEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`userId` INTEGER NOT NULL, `type` INTEGER NOT NULL, `timeUtc` INTEGER NOT NULL, `value` REAL NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_HealthMetricEntity_type_timeUtc` ON `HealthMetricEntity` (`type`, `timeUtc`)")
            }
        }

        /** The insights table is called patterns now. */
        /** Meal activity curves, personal activity models and pipeline state (replaces the on-the-fly Beta(2,3) carb curve). */
        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `MealActivityEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, " +
                        "`mealId` INTEGER NOT NULL, `mealTimeUtc` INTEGER NOT NULL, `algorithmVersion` INTEGER NOT NULL, `method` TEXT NOT NULL, " +
                        "`modelVersion` INTEGER, `trainingRevision` TEXT, `inputFingerprint` TEXT NOT NULL, `quality` TEXT NOT NULL, `qualityNotes` TEXT, " +
                        "`horizonMinutes` INTEGER NOT NULL, `stepMinutes` INTEGER NOT NULL, `activityWindowHours` REAL NOT NULL, `carbsG` REAL NOT NULL, " +
                        "`carbsRemainingG` TEXT NOT NULL, `carbsRateGph` TEXT NOT NULL, `proteinRemainingG` TEXT, `proteinActivityGph` TEXT, " +
                        "`fatRemainingG` TEXT, `fatActivityGph` TEXT, `generatedAtUtc` INTEGER NOT NULL, `status` TEXT NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_MealActivityEntity_mealId_inputFingerprint` ON `MealActivityEntity` (`mealId`, `inputFingerprint`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_MealActivityEntity_mealId_status` ON `MealActivityEntity` (`mealId`, `status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_MealActivityEntity_userId_status` ON `MealActivityEntity` (`userId`, `status`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `PersonalActivityModelEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER NOT NULL, " +
                        "`version` INTEGER NOT NULL, `status` TEXT NOT NULL, `adjustJson` TEXT NOT NULL, `datasetRevision` TEXT NOT NULL, `algoKey` TEXT NOT NULL, " +
                        "`trainedAtUtc` INTEGER NOT NULL, `episodeCount` INTEGER NOT NULL, `reportJson` TEXT NOT NULL, `note` TEXT)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_PersonalActivityModelEntity_userId_version` ON `PersonalActivityModelEntity` (`userId`, `version`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_PersonalActivityModelEntity_userId_status` ON `PersonalActivityModelEntity` (`userId`, `status`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ActivityPipelineStateEntity` (`userId` INTEGER NOT NULL, `processedRevision` TEXT, `processedAlgoKey` TEXT, " +
                        "`lastFullRunUtc` INTEGER NOT NULL, `lastRunUtc` INTEGER NOT NULL, `lastResult` TEXT NOT NULL, `lastMessage` TEXT, " +
                        "`eligibleEpisodes` INTEGER NOT NULL, `curvesRecalculated` INTEGER NOT NULL, `curvesTotal` INTEGER NOT NULL, PRIMARY KEY(`userId`))"
                )
            }
        }

        /** Favourites among saved products, and which database a product came from (existing ones stay visible as favourites). */
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `SavedProductEntity` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `SavedProductEntity` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'Open Food Facts'")
                db.execSQL("UPDATE `SavedProductEntity` SET `isFavorite` = 1")
            }
        }

        /** Products scanned by barcode and saved in a meal, for picking them again. */
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `SavedProductEntity` (`barcode` TEXT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT, " +
                        "`kcal100` REAL NOT NULL, `carbs100` REAL NOT NULL, `sugar100` REAL NOT NULL, `fiber100` REAL NOT NULL, " +
                        "`protein100` REAL NOT NULL, `fat100` REAL NOT NULL, `servingG` REAL, `packageG` REAL, " +
                        "`lastUsedUtc` INTEGER NOT NULL, `useCount` INTEGER NOT NULL, PRIMARY KEY(`barcode`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_SavedProductEntity_lastUsedUtc` ON `SavedProductEntity` (`lastUsedUtc`)")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `InsightEntity` RENAME TO `PatternEntity`")
                db.execSQL("DROP INDEX IF EXISTS `index_InsightEntity_state`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_PatternEntity_state` ON `PatternEntity` (`state`)")
            }
        }

        /** Insights (own page and badge); the glucose-pattern and situation entries leave the bell list. */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `InsightEntity` (`id` TEXT NOT NULL, `state` TEXT NOT NULL, `isRead` INTEGER NOT NULL, " +
                        "`priority` REAL NOT NULL, `firstSeenUtc` INTEGER NOT NULL, `lastSeenUtc` INTEGER NOT NULL, " +
                        "`missedScans` INTEGER NOT NULL, `referenceEffect` REAL NOT NULL, `notifiedAtUtc` INTEGER, " +
                        "`payload` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_InsightEntity_state` ON `InsightEntity` (`state`)")
                db.execSQL("DELETE FROM AppNotificationEntity WHERE link LIKE 'patterns:%' OR dedupeKey LIKE 'situation-%'")
            }
        }

        /** Notifications can be removed by the user (soft delete, so they aren't raised again). */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE AppNotificationEntity ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Notifications say what they open (Devices page, Patterns page with highlights). */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE AppNotificationEntity ADD COLUMN link TEXT")
            }
        }

        /** In-app notifications (device expiry alerts). */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `AppNotificationEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`timestampUtc` INTEGER NOT NULL, `title` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`dedupeKey` TEXT NOT NULL, `isRead` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_AppNotificationEntity_dedupeKey` ON `AppNotificationEntity` (`dedupeKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_AppNotificationEntity_timestampUtc` ON `AppNotificationEntity` (`timestampUtc`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE MealEntity ADD COLUMN sourceId TEXT"
                )
            }
        }

        /** Courses: meals sharing a sittingId form one extended meal; extra photos per course. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE MealEntity ADD COLUMN sittingId TEXT")
                db.execSQL("ALTER TABLE MealEntity ADD COLUMN extraImageIds TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_MealEntity_sittingId ON MealEntity (sittingId)")
            }
        }

        /** Merging imported AAPS carb entries into logged meals. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE MealEntity ADD COLUMN mergedIntoId INTEGER")
                db.execSQL("ALTER TABLE MealEntity ADD COLUMN mergeDeclined INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE MealEntity ADD COLUMN estimatedCarbs INTEGER")
                db.execSQL("ALTER TABLE MealEntity ADD COLUMN aapsLinked INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** AI-identified meal components (JSON list). */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE MealEntity ADD COLUMN components TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** User-chosen name of a whole meal. */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE MealEntity ADD COLUMN sittingName TEXT")
            }
        }

        /** Non-bolus AAPS treatments (pod/site changes, temp basals, ...). */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `PumpEventEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`userId` INTEGER NOT NULL, `timestampUtc` INTEGER NOT NULL, `createdAtUtc` INTEGER NOT NULL, " +
                        "`eventType` TEXT NOT NULL, `notes` TEXT, `durationMinutes` REAL, `rate` REAL, " +
                        "`sourceId` TEXT NOT NULL, `rawJson` TEXT NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_PumpEventEntity_sourceId` ON `PumpEventEntity` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_PumpEventEntity_timestampUtc` ON `PumpEventEntity` (`timestampUtc`)")
            }
        }

        /** Device events can be removed by the user (soft delete). */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE PumpEventEntity ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Health Connect activity data: heart rate, steps, sleep stages, exercise sessions. */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `HeartRateEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`userId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `bpm` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_HeartRateEntity_timestamp` ON `HeartRateEntity` (`timestamp`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `StepsEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`userId` INTEGER NOT NULL, `startUtc` INTEGER NOT NULL, `count` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_StepsEntity_startUtc` ON `StepsEntity` (`startUtc`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `SleepStageEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`userId` INTEGER NOT NULL, `sessionId` TEXT NOT NULL, `startUtc` INTEGER NOT NULL, " +
                        "`endUtc` INTEGER NOT NULL, `stage` INTEGER NOT NULL, `sessionStartUtc` INTEGER NOT NULL, " +
                        "`sessionEndUtc` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_SleepStageEntity_sessionId_startUtc` ON `SleepStageEntity` (`sessionId`, `startUtc`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_SleepStageEntity_endUtc` ON `SleepStageEntity` (`endUtc`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ExerciseEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`userId` INTEGER NOT NULL, `sourceId` TEXT NOT NULL, `startUtc` INTEGER NOT NULL, " +
                        "`endUtc` INTEGER NOT NULL, `exerciseType` INTEGER NOT NULL, `title` TEXT)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ExerciseEntity_sourceId` ON `ExerciseEntity` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ExerciseEntity_endUtc` ON `ExerciseEntity` (`endUtc`)")
            }
        }
    }
}
