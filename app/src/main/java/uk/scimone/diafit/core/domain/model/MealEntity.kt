package uk.scimone.diafit.core.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One **course**: food that arrived/was eaten at one moment, with its own time, nutrition and absorption.
 * Courses sharing a [sittingId] form one **meal** (a sitting, e.g. starter + main + dessert, or the
 * plates of an all-you-can-eat sushi dinner), see [MealSitting]. A meal with one course is the common case.
 */
@Entity(indices = [Index(value = ["sittingId"])])
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
    val sourceId: String? = null,
    /** Groups the courses of one meal; null for imported entries and meals logged before courses existed. */
    val sittingId: String? = null,
    /** User-chosen name of the whole meal (copied onto every course of the sitting); null = derive from the courses. */
    val sittingName: String? = null,
    /** Photos beyond the cover [imageId], in display order. */
    @ColumnInfo(defaultValue = "[]")
    val extraImageIds: List<String> = emptyList(),
    /** Imported carb entry that was folded into the logged meal with this id; hidden everywhere (see [MealMatcher]). */
    val mergedIntoId: Int? = null,
    /** Imported entry the user declined to merge (or unlinked); never matched again. */
    @ColumnInfo(defaultValue = "0")
    val mergeDeclined: Boolean = false,
    /** Logged meal whose carbs were replaced by the dosed amount from AAPS: the original (AI/typed) estimate. */
    val estimatedCarbs: Int? = null,
    /** Logged meal that an imported AAPS carb entry was merged into. */
    @ColumnInfo(defaultValue = "0")
    val aapsLinked: Boolean = false,
    /** The foods of this course as identified by the AI (editable); the nutrition totals are their sum unless overridden. */
    @ColumnInfo(defaultValue = "[]")
    val components: List<MealComponent> = emptyList()
) {
    /** Every photo of this course, cover first. */
    val photoIds: List<String> get() = (listOf(imageId) + extraImageIds).filter { it.isNotEmpty() }


    companion object {
        /**
         * Absorption speed from the macro mix: the energy from fat and protein relative to the carbs
         * slows digestion (pizza, burgers), so the ratio decides rather than absolute amounts. It is
         * scale-invariant, so adjusting a portion doesn't flip the suggestion.
         */
        fun inferImpactType(carbs: Int?, proteins: Int?, fats: Int?): ImpactType {
            val c = carbs ?: 0
            if (c <= 0) return ImpactType.MEDIUM
            val f = fats ?: 0
            val p = proteins ?: 0
            if (f >= 35) return ImpactType.LONG
            val ratio = (9.0 * f + 4.0 * p) / (4.0 * c)
            return when {
                ratio < 0.4 -> ImpactType.SHORT
                ratio < 1.0 -> ImpactType.MEDIUM
                else -> ImpactType.LONG
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