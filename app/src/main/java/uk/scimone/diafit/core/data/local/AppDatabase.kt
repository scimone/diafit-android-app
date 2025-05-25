package uk.scimone.diafit.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import uk.scimone.diafit.core.domain.model.MealEntity

@Database(
    entities = [MealEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
}
