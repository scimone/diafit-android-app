package uk.scimone.diafit.home.presentation.model

data class CarbsChartData(
    override val timeFloat: Float,
    override val value: Int
) : ChartData

fun MealEntityUi.toChartData(): CarbsChartData {
    return CarbsChartData(
        timeFloat = this.mealTimeUtc.toFloat(),
        value = this.carbohydrates
    )
}