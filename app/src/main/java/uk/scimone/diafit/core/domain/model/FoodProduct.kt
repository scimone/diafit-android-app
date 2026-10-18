package uk.scimone.diafit.core.domain.model

/** Nutrients per 100 g (or 100 ml for drinks; 1 ml is treated as 1 g, as for AI estimates). */
data class NutrientsPer100(
    val kcal: Double,
    val carbsG: Double,
    val sugarG: Double,
    val fiberG: Double,
    val proteinG: Double,
    val fatG: Double
)

/** A packaged food found by its barcode. */
data class FoodProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val per100: NutrientsPer100,
    /** Weight of one serving as printed on the pack, if the database knows it. */
    val servingG: Double?,
    /** Weight of the whole pack, if known. */
    val packageG: Double?,
    /** The database it came from, shown to the user. */
    val source: String = SOURCE_OFF
) {
    /** What a first portion defaults to: one serving, else 100 g. */
    val defaultPortionG: Double get() = servingG ?: 100.0

    /** This product as a meal component weighing [portionG]; nutrients scale with the weight. */
    fun toComponent(portionG: Double = defaultPortionG): MealComponent {
        val f = portionG / 100.0
        return MealComponent(
            name = name,
            emoji = if (barcode.isRecipeId()) "🍲" else "📦",
            basis = listOfNotNull(
                source,
                servingG?.let { "serving ${formatGrams(it)}" } ?: "per 100 g",
                barcode
            ).joinToString(" · "),
            weightG = portionG,
            calories = per100.kcal * f,
            carbsG = per100.carbsG * f,
            sugarG = per100.sugarG * f,
            fiberG = per100.fiberG * f,
            proteinG = per100.proteinG * f,
            fatG = per100.fatG * f,
            confidence = ComponentConfidence.HIGH,
            servingG = servingG,
            packageG = packageG,
            barcode = barcode
        )
    }
}

const val SOURCE_OFF = "Open Food Facts"
const val SOURCE_USDA = "USDA FoodData Central"

fun formatGrams(g: Double): String = if (g % 1.0 == 0.0) "${g.toInt()} g" else "%.1f g".format(g)

sealed interface ProductLookup {
    data class Found(val product: FoodProduct) : ProductLookup
    /** The database has no entry for this barcode, or none with usable nutrition data. */
    data class NotFound(val barcode: String) : ProductLookup
    data class Failed(val barcode: String, val reason: String) : ProductLookup
}
