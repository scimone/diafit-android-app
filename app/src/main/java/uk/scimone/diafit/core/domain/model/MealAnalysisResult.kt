package uk.scimone.diafit.core.domain.model

data class MealAnalysisResult(
    val mealName: String?,
    val components: List<MealComponent>,
    val reasoning: String?,
    val impactType: ImpactType
) {
    /** Totals are always the sum of the components, so they can't contradict the cards. */
    val totals: ComponentTotals get() = components.totals()
}
