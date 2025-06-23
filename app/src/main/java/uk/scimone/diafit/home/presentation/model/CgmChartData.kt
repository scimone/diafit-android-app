package uk.scimone.diafit.home.presentation.model

import uk.scimone.diafit.core.domain.model.CgmEntity

data class CgmChartData (
    val timeLong: Long,
    val value: Int
)

fun CgmEntity.toChartData(): CgmChartData {
    return CgmChartData(
        timeLong = this.timestamp,
        value = this.valueMgdl
    )
}