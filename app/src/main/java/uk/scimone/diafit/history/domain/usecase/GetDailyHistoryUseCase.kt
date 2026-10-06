package uk.scimone.diafit.history.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import uk.scimone.diafit.core.domain.usecase.GetAllBolusSinceUseCase
import uk.scimone.diafit.core.domain.usecase.GetAllCgmSinceUseCase
import uk.scimone.diafit.core.domain.usecase.GetAllMealsSinceUseCase
import uk.scimone.diafit.history.domain.model.DayHistory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** CGM, bolus and meal data of the last [days] local days, grouped per day, newest day first. Every day is included, empty or not, so the tracks stay a fixed grid. */
class GetDailyHistoryUseCase(
    private val getAllCgmSince: GetAllCgmSinceUseCase,
    private val getAllBolusSince: GetAllBolusSinceUseCase,
    private val getAllMealsSince: GetAllMealsSinceUseCase
) {
    operator fun invoke(userId: Int, days: Int, zone: ZoneId = ZoneId.systemDefault()): Flow<List<DayHistory>> {
        val start = LocalDate.now(zone).minusDays(days - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        fun dayOf(timeUtc: Long) = Instant.ofEpochMilli(timeUtc).atZone(zone).toLocalDate()

        return combine(
            getAllCgmSince(start, userId),
            getAllBolusSince(start, userId),
            getAllMealsSince(start, userId)
        ) { cgm, boluses, meals ->
            val cgmByDay = cgm.groupBy { dayOf(it.timestamp) }
            val bolusByDay = boluses.groupBy { dayOf(it.timestampUtc) }
            val mealsByDay = meals.filter { it.carbohydrates > 0 }.groupBy { dayOf(it.mealTimeUtc) }
            val today = LocalDate.now(zone)
            (0 until days)
                .map { today.minusDays(it.toLong()) }
                .map { date ->
                    DayHistory(
                        date = date,
                        readings = cgmByDay[date].orEmpty(),
                        boluses = bolusByDay[date].orEmpty(),
                        meals = mealsByDay[date].orEmpty()
                    )
                }
        }
    }
}
