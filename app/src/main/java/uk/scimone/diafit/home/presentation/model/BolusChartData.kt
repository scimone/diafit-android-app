package uk.scimone.diafit.home.presentation.model

import uk.scimone.diafit.core.domain.model.BolusEntity

data class BolusChartData(
    override val timeLong: Long,
    override val value: Float
) : ChartData

fun BolusEntity.toChartData(): BolusChartData {
    return BolusChartData(
        timeLong = this.timestampUtc.toLong(),
        value = this.value
    )
}