package uk.scimone.diafit.core.domain.model

data class MealAnalysisResult(
    val dishName: String?,
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
