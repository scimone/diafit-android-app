package uk.scimone.diafit.home.presentation

import uk.scimone.diafit.home.presentation.model.CgmChartData
import uk.scimone.diafit.home.presentation.model.CgmEntityUi


data class HomeState(
    val cgmUi: CgmEntityUi? = null,
    val cgmHistory: List<CgmChartData> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)