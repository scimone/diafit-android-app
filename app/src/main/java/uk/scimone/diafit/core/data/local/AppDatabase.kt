package uk.scimone.diafit.core.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.MealEntity

@Database(
    entities = [MealEntity::class, CgmEntity::class, BolusEntity::class],
    version = 10,
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
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun cgmDao(): CgmDao
    abstract fun bolusDao(): BolusDao

    companion object {
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE MealEntity ADD COLUMN sourceId TEXT"
                )
            }
        }
    }
}
