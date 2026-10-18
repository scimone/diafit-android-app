package uk.scimone.diafit.core.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Prefix of the product id a recipe uses when it is logged like a scanned food. */
const val RECIPE_ID_PREFIX = "recipe:"
const val SOURCE_RECIPE = "Recipe"

/**
 * A dish the user cooks: its ingredients with their weights (each a [MealComponent], so scanned, searched,
 * AI-estimated and typed foods all fit) and, optionally, what the finished food weighs and how many
 * portions it makes. Logging a meal from a recipe only needs the eaten weight.
 */
@Entity(indices = [Index(value = ["lastUsedUtc"])])
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val ingredients: List<MealComponent>,
    /** Weight of everything after cooking (water evaporates or soaks in); null = the raw ingredients' weight. */
    val finishedWeightG: Double?,
    /** How many equal portions the dish makes, if the user thinks in portions. */
    val servings: Int?,
    val createdUtc: Long,
    val updatedUtc: Long,
    val lastUsedUtc: Long? = null,
    val useCount: Int = 0
) {
    val rawWeightG: Double get() = ingredients.sumOf { it.weightG }

    /** The weight the nutrients are spread over. */
    val effectiveWeightG: Double get() = effectiveRecipeWeight(ingredients, finishedWeightG)

    val totals: ComponentTotals get() = ingredients.totals()

    val per100: NutrientsPer100? get() = recipePer100(ingredients, finishedWeightG)

    /** Weight of one portion, when the recipe has portions. */
    val servingG: Double? get() = servings?.takeIf { it > 0 }?.let { effectiveWeightG / it }

    /** This recipe as a food the meal flow can portion by weight; null while it can't be computed (no weight). */
    fun toProduct(): FoodProduct? {
        val per = per100 ?: return null
        return FoodProduct(
            barcode = "$RECIPE_ID_PREFIX$id", name = name, brand = null, per100 = per,
            servingG = servingG, packageG = null, source = SOURCE_RECIPE
        )
    }
}

fun effectiveRecipeWeight(ingredients: List<MealComponent>, finishedWeightG: Double?): Double =
    finishedWeightG?.takeIf { it > 0 } ?: ingredients.sumOf { it.weightG }

/** Nutrients per 100 g of the finished food: all ingredients' nutrients over the finished weight. */
fun recipePer100(ingredients: List<MealComponent>, finishedWeightG: Double?): NutrientsPer100? {
    val w = effectiveRecipeWeight(ingredients, finishedWeightG)
    if (ingredients.isEmpty() || w <= 0.0) return null
    val k = 100.0 / w
    return NutrientsPer100(
        kcal = ingredients.sumOf { it.calories } * k,
        carbsG = ingredients.sumOf { it.carbsG } * k,
        sugarG = ingredients.sumOf { it.sugarG } * k,
        fiberG = ingredients.sumOf { it.fiberG } * k,
        proteinG = ingredients.sumOf { it.proteinG } * k,
        fatG = ingredients.sumOf { it.fatG } * k
    )
}

fun String?.isRecipeId(): Boolean = this?.startsWith(RECIPE_ID_PREFIX) == true

fun String.recipeIdOrNull(): Int? = takeIf { it.isRecipeId() }?.removePrefix(RECIPE_ID_PREFIX)?.toIntOrNull()
