package uk.scimone.diafit.home.presentation.model

import uk.scimone.diafit.core.domain.model.BolusEntity

data class BolusChartData(
    val timeFloat: Float,
    val value: Float
    )

fun BolusEntity.toChartData(): BolusChartData {
    return BolusChartData(
        timeFloat = this.timestampUtc.toFloat(),
        value = this.value
    )
}