package uk.scimone.diafit.home.presentation

import uk.scimone.diafit.home.presentation.model.BolusChartData
import uk.scimone.diafit.home.presentation.model.CgmChartData
import uk.scimone.diafit.home.presentation.model.CgmEntityUi
import uk.scimone.diafit.home.presentation.model.MealEntityUi


data class HomeState(
    val cgmUi: CgmEntityUi? = null,
    val cgmHistory: List<CgmChartData> = emptyList(),
    val bolusHistory: List<BolusChartData> = emptyList(),
    val mealHistory: List<MealEntityUi> = emptyList(),
    val targetRangeLower: Int = 70,
    val targetRangeUpper: Int = 180,
    val isLoading: Boolean = true,
    val error: String? = null
)