package uk.scimone.diafit.home.presentation.model

import uk.scimone.diafit.mealactivity.domain.MealActivityCurve

/** A meal on the carb panel: [value] grams eaten at [timeLong], with its activity curve (the charts draw its absorption rate, g/h). */
data class CarbsChartData(
    override val timeLong: Long,
    override val value: Int,
    val curve: MealActivityCurve
) : ChartData

fun MealEntityUi.toChartData(): CarbsChartData =
    CarbsChartData(timeLong = mealTimeUtc, value = carbohydrates, curve = activity)
