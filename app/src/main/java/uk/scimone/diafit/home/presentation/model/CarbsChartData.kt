package uk.scimone.diafit.home.presentation.model

data class CarbsChartData(
    override val timeLong: Long,
    override val value: Int
) : ChartData

fun MealEntityUi.toChartData(): CarbsChartData {
    return CarbsChartData(
        timeLong = this.mealTimeUtc,
        value = this.carbohydrates
    )
}