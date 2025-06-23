package uk.scimone.diafit.home.presentation.model

import uk.scimone.diafit.core.domain.model.BolusEntity

data class InsulinActivityChartData(
    val timeMillis: Long,
    val value: Float
)

fun BolusEntity.toInsulinActivityChartData(): InsulinActivityChartData {
    return InsulinActivityChartData(
        timeMillis = this.timestampUtc,
        value = this.value
    )
}