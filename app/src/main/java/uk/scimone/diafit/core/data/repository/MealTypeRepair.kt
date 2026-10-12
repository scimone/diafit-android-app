package uk.scimone.diafit.core.data.repository

import android.content.Context
import android.util.Log
import uk.scimone.diafit.core.data.local.MealDao
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealType

/**
 * One-time fix: photo-less imports (AAPS / Nightscout carbs) were all stored as snacks. Re-derives their type from
 * time of day and carbs, exactly like a newly added meal. Only rows still marked as snack are touched.
 */
class MealTypeRepair(private val dao: MealDao, context: Context) {
    private val prefs = context.getSharedPreferences("meal_type_repair", Context.MODE_PRIVATE)

    suspend fun runOnce() {
        if (prefs.getBoolean(KEY, false)) return
        try {
            var changed = 0
            for (meal in dao.getImportedWithoutPhoto()) {
                if (meal.mealType != MealType.SNACK) continue
                val type = MealEntity.suggestMealTypeAt(meal.mealTimeUtc, meal.carbohydrates)
                if (type != MealType.SNACK) { dao.setMealType(meal.id, type); changed++ }
            }
            prefs.edit().putBoolean(KEY, true).apply()
            Log.i(TAG, "Re-typed $changed imported meals")
        } catch (e: Exception) {
            Log.e(TAG, "Meal type repair failed", e)
        }
    }

    private companion object {
        const val TAG = "MealTypeRepair"
        const val KEY = "repair_v1"
    }
}
