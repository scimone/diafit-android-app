package uk.scimone.diafit.home.presentation.model

import uk.scimone.diafit.core.domain.model.CgmEntity

data class CgmChartData (
    override val timeLong: Long,
    override val value: Int
) : ChartData

fun CgmEntity.toChartData(): CgmChartData {
    return CgmChartData(
        timeLong = this.timestamp,
        value = this.valueMgdl
    )
}