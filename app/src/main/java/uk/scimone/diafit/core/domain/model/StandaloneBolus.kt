package uk.scimone.diafit.core.domain.model

/**
 * Manual boluses (corrections) that belong to no meal: not within a sitting's dose window
 * ([MEAL_DOSE_LEAD_MS] before its first course to [MEAL_DOSE_TAIL_MS] after its last). SMBs are
 * automatic micro-boluses, far too many to list, so they are left out.
 */
fun List<BolusEntity>.standalone(sittings: List<MealSitting>): List<BolusEntity> = filter { bolus ->
    !bolus.isSmb && sittings.none { bolus.timestampUtc in (it.startTime - MEAL_DOSE_LEAD_MS)..(it.endTime + MEAL_DOSE_TAIL_MS) }
}
