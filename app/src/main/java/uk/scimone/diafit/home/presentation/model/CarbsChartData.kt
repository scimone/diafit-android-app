package uk.scimone.diafit.home.presentation.model

data class CarbsChartData(
    override val timeLong: Long,
    override val value: Int,
    val durationMinutes: Int = 240,
    val imageUri: android.net.Uri? = null
) : ChartData

fun MealEntityUi.toChartData(): CarbsChartData {
    return CarbsChartData(
        timeLong = this.mealTimeUtc,
        value = this.carbohydrates,
        durationMinutes = this.impactType.durationMinutes,
        imageUri = this.imageUri
    )
}