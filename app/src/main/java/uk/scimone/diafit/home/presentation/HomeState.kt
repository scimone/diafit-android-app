package uk.scimone.diafit.home.presentation

import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.BasalSegment
import uk.scimone.diafit.home.presentation.model.BolusChartData
import uk.scimone.diafit.home.presentation.model.CarbsChartData
import uk.scimone.diafit.home.presentation.model.CgmChartData
import uk.scimone.diafit.home.presentation.model.CgmEntityUi
import uk.scimone.diafit.home.presentation.model.InsulinActivityChartData
import uk.scimone.diafit.home.presentation.model.MealEntityUi


data class HomeState(
    val cgmUi: CgmEntityUi? = null,
    val cgmHistory: List<CgmChartData> = emptyList(),
    val bolusHistory: List<BolusChartData> = emptyList(),
    val insulinActivityHistory: List<InsulinActivityChartData> = emptyList(),
    val carbHistory: List<CarbsChartData> = emptyList(),
    val mealHistory: List<MealEntityUi> = emptyList(),
    val basal: List<BasalSegment> = emptyList(),
    val smbs: List<uk.scimone.diafit.core.domain.model.SmbMark> = emptyList(),
    val basalStyle: uk.scimone.diafit.settings.domain.model.BasalStyle = uk.scimone.diafit.settings.domain.model.BasalStyle.RATE,
    val activity: ActivityData = ActivityData(),
    /** Health Connect import switched on (decides which "no activity data" message to show). */
    val activityConnected: Boolean = false,
    val targetRangeLower: Int = 70,
    val targetRangeUpper: Int = 180,
    /** The 24 h profile of the last two weeks behind the glucose curve (toggled by the quick button). */
    val showAgp: Boolean = false,
    val agp: uk.scimone.diafit.patterns.domain.PatternAgp? = null,
    /** AGP was asked for but there is not enough glucose data to build one. */
    val agpUnavailable: Boolean = false,
    /** A glucose source is selected (decides between "connect your data" and "waiting for the first reading"). */
    val cgmSourceSelected: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)