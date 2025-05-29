package uk.scimone.diafit.home.presentation.model

import uk.scimone.diafit.core.domain.model.CgmEntity

data class CgmChartData (
    val timeFloat: Float,
    val value: Int
)

fun CgmEntity.toChartData(): CgmChartData {
    return CgmChartData(
        timeFloat = this.timestamp.toFloat(),
        value = this.valueMgdl
    )
}