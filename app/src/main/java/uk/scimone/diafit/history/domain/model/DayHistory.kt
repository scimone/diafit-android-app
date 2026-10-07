package uk.scimone.diafit.history.domain.model

import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.MealEntity
import java.time.LocalDate

/** Everything recorded on one local calendar day. */
data class DayHistory(
    val date: LocalDate,
    val readings: List<CgmEntity>,
    val boluses: List<BolusEntity>,
    val meals: List<MealEntity>,
    /** Sleep and exercise overlapping the day (no heart rate or steps: too heavy for long ranges). */
    val activity: ActivityData = ActivityData()
)
