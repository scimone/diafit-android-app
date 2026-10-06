package uk.scimone.diafit.history.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.repository.BolusRepository
import uk.scimone.diafit.core.domain.repository.CgmRepository
import uk.scimone.diafit.core.domain.usecase.GetAllMealsSinceUseCase
import java.time.LocalDate
import java.time.ZoneId

/** Everything needed to show one day in full. */
data class DayDetail(
    val date: LocalDate,
    val dayStartUtc: Long,
    val dayEndUtc: Long,
    /** The day's readings plus [GetDayDetailUseCase.OUTCOME_TAIL_MS] after midnight, for late meals' outcomes. */
    val readings: List<CgmEntity>,
    /** Boluses of the day, plus a margin either side so a meal near midnight still finds its insulin. */
    val boluses: List<BolusEntity>,
    /** Every valid course eaten that day. */
    val meals: List<MealEntity>
)

/**
 * One local day's CGM, insulin and meals. Meals are observed (edits and deletes show up at once);
 * CGM and boluses are read by range, re-read every minute while the day is still running.
 */
class GetDayDetailUseCase(
    private val cgmRepository: CgmRepository,
    private val bolusRepository: BolusRepository,
    private val getAllMealsSince: GetAllMealsSinceUseCase
) {
    operator fun invoke(userId: Int, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<DayDetail> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val ticks = flow {
            emit(Unit)
            while (System.currentTimeMillis() < end + OUTCOME_TAIL_MS) {
                delay(REFRESH_MS)
                emit(Unit)
            }
        }
        return combine(getAllMealsSince(start, userId), ticks) { meals, _ ->
            DayDetail(
                date = date,
                dayStartUtc = start,
                dayEndUtc = end,
                readings = cgmRepository.getEntriesBetween(start, end + OUTCOME_TAIL_MS, userId),
                boluses = bolusRepository.getBolusBetween(start - BOLUS_MARGIN_MS, end + BOLUS_MARGIN_MS, userId),
                meals = meals.filter { it.mealTimeUtc in start until end }
            )
        }
    }

    companion object {
        const val OUTCOME_TAIL_MS = 4 * 60 * 60_000L
        private const val BOLUS_MARGIN_MS = 60 * 60_000L
        private const val REFRESH_MS = 60_000L
    }
}
