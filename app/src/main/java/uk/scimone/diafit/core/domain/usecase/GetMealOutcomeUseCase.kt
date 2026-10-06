package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.core.domain.model.MEAL_DOSE_LEAD_MS
import uk.scimone.diafit.core.domain.model.MEAL_DOSE_TAIL_MS
import uk.scimone.diafit.core.domain.model.MEAL_OUTCOME_WINDOW_MS
import uk.scimone.diafit.core.domain.model.MealOutcome
import uk.scimone.diafit.core.domain.model.MealSitting
import uk.scimone.diafit.core.domain.repository.BolusRepository
import uk.scimone.diafit.core.domain.repository.CgmRepository

/** Loads what [MealOutcome] needs for one meal and computes it. */
class GetMealOutcomeUseCase(
    private val cgmRepository: CgmRepository,
    private val bolusRepository: BolusRepository
) {
    suspend operator fun invoke(userId: Int, sitting: MealSitting, target: GlucoseTargetRange): MealOutcome {
        val start = sitting.startTime
        val readings = cgmRepository.getEntriesBetween(start - START_LEAD_MS, start + MEAL_OUTCOME_WINDOW_MS, userId)
        val boluses = bolusRepository.getBolusBetween(start - MEAL_DOSE_LEAD_MS, sitting.endTime + MEAL_DOSE_TAIL_MS, userId)
        return MealOutcome.of(sitting, readings, boluses, target)
    }

    private companion object {
        /** The "at meal" reading may be a few minutes before the first course. */
        const val START_LEAD_MS = 15 * 60_000L
    }
}
