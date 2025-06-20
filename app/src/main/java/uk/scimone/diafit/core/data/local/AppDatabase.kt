package uk.scimone.diafit.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.MealEntity

@Database(
    entities = [MealEntity::class, CgmEntity::class, BolusEntity::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun cgmDao(): CgmDao
    abstract fun bolusDao(): BolusDao

}
