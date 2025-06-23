package uk.scimone.diafit.home.presentation.model

import uk.scimone.diafit.core.domain.model.BolusEntity

data class BolusChartData(
    override val timeFloat: Float,
    override val value: Float
) : ChartData

fun BolusEntity.toChartData(): BolusChartData {
    return BolusChartData(
        timeFloat = this.timestampUtc.toFloat(),
        value = this.value
    )
}