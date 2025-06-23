package uk.scimone.diafit.core.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val description: String?,
    val createdAtUtc: Long,
    val mealTimeUtc: Long,
    val calories: Int? = 0,
    val carbohydrates: Int = 0,
    val proteins: Int? = 0,
    val fats: Int? = 0,
    val impactType: ImpactType = ImpactType.MEDIUM, // e.g. SHORT, MEDIUM, LONG
    val mealType: MealType = MealType.SNACK, // e.g. Breakfast, Lunch, Dinner, Snack
    val isValid: Boolean = true,
    val imageId: String,
    val recommendation: String?,       // AI recommendation text
    val reasoning: String?,              // AI reasoning text
    val sourceId: String? = null
) {
    companion object {
        fun inferImpactType(carbs: Int?, proteins: Int?, fats: Int?): ImpactType {
            return if (carbs == null || proteins == null || fats == null) {
                ImpactType.MEDIUM
            } else {
                when {
                    carbs >= 60 && proteins >= 30 && fats >= 20 -> ImpactType.LONG
                    carbs >= 40 -> ImpactType.MEDIUM
                    else -> ImpactType.SHORT
                }
            }
        }

        fun inferMealType(hour: Int): MealType {
            return when (hour) {
                in 5 until 10 -> MealType.BREAKFAST
                in 11 until 15 -> MealType.LUNCH
                in 18 until 21 -> MealType.DINNER
                else -> MealType.SNACK
            }
        }
    }
}

enum class ImpactType(val durationMinutes: Int) {
    SHORT(120),   // e.g. simple sugars
    MEDIUM(240),  // e.g. complex carbs
    LONG(360)     // e.g. high fat + carbs like pizza
}

enum class MealType(val type: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack");
    companion object {
        fun fromString(type: String): MealType {
            return values().find { it.type.equals(type, ignoreCase = true) } ?: SNACK
        }
    }
}