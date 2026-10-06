package uk.scimone.diafit.core.domain.model

data class MealAnalysisResult(
    val dishName: String?,
    /** Per-dish carbs when the photos show more than one dish (e.g. several sushi plates). */
    val dishes: List<MealDish> = emptyList(),
    val ingredients: List<MealIngredient>,
    val calories: Int?,
    val protein: Int?,
    val carbohydrates: Int?,
    val fat: Int?,
    val reasoning: String?,
    val impactType: ImpactType
)

data class MealIngredient(
    val name: String,
    val quantity: String?
)

data class MealDish(
    val name: String,
    val carbohydrates: Int?
)
