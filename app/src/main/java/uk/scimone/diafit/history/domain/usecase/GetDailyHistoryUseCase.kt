package uk.scimone.diafit.history.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.repository.ActivityRepository
import uk.scimone.diafit.core.domain.usecase.GetAllBolusSinceUseCase
import uk.scimone.diafit.core.domain.usecase.GetAllCgmSinceUseCase
import uk.scimone.diafit.core.domain.usecase.GetAllMealsSinceUseCase
import uk.scimone.diafit.history.domain.model.DayHistory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** CGM, bolus and meal data of the [days] local days ending [page] pages back, grouped per day, newest day first. Every day is included, empty or not, so the tracks stay a fixed grid. */
class GetDailyHistoryUseCase(
    private val getAllCgmSince: GetAllCgmSinceUseCase,
    private val getAllBolusSince: GetAllBolusSinceUseCase,
    private val getAllMealsSince: GetAllMealsSinceUseCase,
    private val activityRepository: ActivityRepository
) {
    operator fun invoke(userId: Int, days: Int, page: Int = 0, zone: ZoneId = ZoneId.systemDefault()): Flow<List<DayHistory>> {
        // Page 0 ends today; each further page steps back by [days] days.
        val lastDay = LocalDate.now(zone).minusDays(page.toLong() * days)
        val start = lastDay.minusDays(days - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
        fun dayOf(timeUtc: Long) = Instant.ofEpochMilli(timeUtc).atZone(zone).toLocalDate()

        return combine(
            getAllCgmSince(start, userId),
            getAllBolusSince(start, userId),
            getAllMealsSince(start, userId),
            activityRepository.observeOverviewSince(start, userId)
        ) { cgm, boluses, meals, activity ->
            val cgmByDay = cgm.groupBy { dayOf(it.timestamp) }
            val bolusByDay = boluses.groupBy { dayOf(it.timestampUtc) }
            val mealsByDay = meals.filter { it.carbohydrates > 0 }.groupBy { dayOf(it.mealTimeUtc) }
            (0 until days)
                .map { lastDay.minusDays(it.toLong()) }
                .map { date ->
                    val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    DayHistory(
                        date = date,
                        readings = cgmByDay[date].orEmpty(),
                        boluses = bolusByDay[date].orEmpty(),
                        meals = mealsByDay[date].orEmpty(),
                        activity = ActivityData(
                            sleep = activity.sessions.sleep.filter { it.sessionEndUtc > dayStart && it.sessionStartUtc < dayEnd },
                            exercise = activity.sessions.exercise.filter { it.endUtc > dayStart && it.startUtc < dayEnd }
                        ),
                        elevatedActivity = activity.elevated.filter { it.endUtc > dayStart && it.startUtc < dayEnd }
                    )
                }
        }
    }
}
