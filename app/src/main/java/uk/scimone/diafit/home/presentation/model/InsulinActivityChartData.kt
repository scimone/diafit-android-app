package uk.scimone.diafit.home.presentation.model

import uk.scimone.diafit.core.domain.model.BolusEntity

data class InsulinActivityChartData(
    override val timeLong: Long,
    override val value: Float
) : ChartData

fun BolusEntity.toInsulinActivityChartData(): InsulinActivityChartData {
    return InsulinActivityChartData(
        timeLong = this.timestampUtc,
        value = this.value
    )
}