package uk.scimone.diafit.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

enum class ComponentConfidence { LOW, MEDIUM, HIGH }

/**
 * One food of a course as identified by the AI (e.g. "Pumpkin soup", "Walnuts"), with the nutrients
 * of the portion eaten. A course's totals are the sum of its components.
 */
@Serializable
data class MealComponent(
    val name: String,
    val emoji: String = "🍽️",
    /** The portion the AI assumed and how it judged it. */
    val basis: String? = null,
    val weightG: Double = 0.0,
    val calories: Double = 0.0,
    val carbsG: Double = 0.0,
    val sugarG: Double = 0.0,
    val fiberG: Double = 0.0,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val confidence: ComponentConfidence = ComponentConfidence.MEDIUM
) {
    /** The same food at another weight: every nutrient scales proportionally. */
    fun withWeight(newWeightG: Double): MealComponent {
        if (weightG <= 0.0) return copy(weightG = newWeightG)
        val f = newWeightG / weightG
        return copy(
            weightG = newWeightG, calories = calories * f, carbsG = carbsG * f, sugarG = sugarG * f,
            fiberG = fiberG * f, proteinG = proteinG * f, fatG = fatG * f
        )
    }
}

/** Energy from the macros (Atwater factors: 4 kcal/g carbs and protein, 9 kcal/g fat). */
fun kcalOf(carbsG: Double, proteinG: Double, fatG: Double): Double = 4 * carbsG + 4 * proteinG + 9 * fatG

/** The same food with edited macros; its energy is recalculated from them. */
fun MealComponent.withMacros(carbsG: Double = this.carbsG, proteinG: Double = this.proteinG, fatG: Double = this.fatG) =
    copy(carbsG = carbsG, proteinG = proteinG, fatG = fatG, calories = kcalOf(carbsG, proteinG, fatG))

data class ComponentTotals(val calories: Int, val carbs: Int, val protein: Int, val fat: Int)

fun List<MealComponent>.totals() = ComponentTotals(
    calories = sumOf { it.calories }.roundToInt(),
    carbs = sumOf { it.carbsG }.roundToInt(),
    protein = sumOf { it.proteinG }.roundToInt(),
    fat = sumOf { it.fatG }.roundToInt()
)
