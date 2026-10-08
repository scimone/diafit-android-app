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
        uk.scimone.diafit.core.domain.model.AppNotificationEntity::class
    ],
    version = 18,
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
    abstract fun cgmDao(): CgmDao
    abstract fun bolusDao(): BolusDao
    abstract fun pumpEventDao(): PumpEventDao
    abstract fun activityDao(): ActivityDao
    abstract fun notificationDao(): NotificationDao

    companion object {
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
