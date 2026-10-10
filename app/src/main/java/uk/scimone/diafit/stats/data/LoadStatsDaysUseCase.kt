package uk.scimone.diafit.stats.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.scimone.diafit.core.data.local.ActivityDao
import uk.scimone.diafit.core.data.local.BolusDao
import uk.scimone.diafit.core.data.local.CgmDao
import uk.scimone.diafit.core.data.local.MealDao
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.ElevatedActivity
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.core.domain.model.HealthMetricType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.SleepSession
import uk.scimone.diafit.core.domain.model.SleepStage
import uk.scimone.diafit.core.domain.model.deliveredUnits
import uk.scimone.diafit.core.domain.model.unionDurationMs
import uk.scimone.diafit.core.domain.usecase.GetBasalTimelineUseCase
import uk.scimone.diafit.stats.domain.GlucoseDay
import uk.scimone.diafit.stats.domain.SleepNight
import uk.scimone.diafit.stats.domain.SleepSpan
import uk.scimone.diafit.stats.domain.StatsDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads one [StatsDay] per local day of a date range. The big tables (CGM, heart rate, steps, calories) are
 * grouped by day in SQL, so a year of per-minute readings never has to be loaded; the small ones (sleep,
 * workouts, meals, boluses) are read as rows.
 */
class LoadStatsDaysUseCase(
    private val cgmDao: CgmDao,
    private val activityDao: ActivityDao,
    private val bolusDao: BolusDao,
    private val mealDao: MealDao,
    private val getBasalTimeline: GetBasalTimelineUseCase
) {
    suspend operator fun invoke(
        userId: Int,
        first: LocalDate,
        lastExclusive: LocalDate,
        thresholds: GlucoseThresholds,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis()
    ): List<StatsDay> = withContext(Dispatchers.Default) {
        val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = lastExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val segments = offsetSegments(zone, start, end)
        fun startOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
        fun dayOf(timeUtc: Long) = Instant.ofEpochMilli(timeUtc).atZone(zone).toLocalDate()

        // ---- grouped in SQL (one query per stretch with a constant UTC offset)
        val glucose = segments.flatMap { (s, e, off) ->
            cgmDao.getDailyGlucose(s, e, off, thresholds.veryLow, thresholds.low, thresholds.high, thresholds.veryHigh, userId)
        }.groupBy { it.day }.mapValues { (_, rows) ->
            GlucoseDay.from(rows.sumOf { it.n }, rows.sumOf { it.sum }, rows.sumOf { it.sumSq }, rows.sumOf { it.low }, rows.sumOf { it.high })
        }
        val heartRate = segments.flatMap { (s, e, off) -> activityDao.getDailyHeartRate(s, e, off, userId) }
            .groupBy { it.day }.mapValues { (_, rows) -> rows.sumOf { it.avg * it.n } / rows.sumOf { it.n } }
        val steps = segments.flatMap { (s, e, off) -> activityDao.getDailySteps(s, e, off, userId) }
            .groupBy { it.day }.mapValues { (_, rows) -> rows.sumOf { it.total } }
        suspend fun metricPerDay(type: HealthMetricType) = segments.flatMap { (s, e, off) -> activityDao.getDailyMetric(type.code, s, e, off, userId) }
            .groupBy { it.day }
        val resting = metricPerDay(HealthMetricType.RESTING_HEART_RATE).mapValues { (_, r) -> r.sumOf { it.avg * it.n } / r.sumOf { it.n } }
        val hrv = metricPerDay(HealthMetricType.HEART_RATE_VARIABILITY).mapValues { (_, r) -> r.sumOf { it.avg * it.n } / r.sumOf { it.n } }
        val totalKcal = metricPerDay(HealthMetricType.TOTAL_CALORIES).mapValues { (_, r) -> r.sumOf { it.sum } }
        val activeKcal = metricPerDay(HealthMetricType.ACTIVE_CALORIES).mapValues { (_, r) -> r.sumOf { it.sum } }

        // ---- rows
        // A night that ends in the window may have started the evening before it.
        val sleepRows = activityDao.getSleepBetween(start - 12 * HOUR_MS, end, userId)
        val nights = ActivityData(sleep = sleepRows).sleepSessions
            .filter { dayOf(it.endUtc) >= first && dayOf(it.endUtc) < lastExclusive }
            .groupBy { dayOf(it.endUtc) }
            .mapValues { (date, sessions) -> nightOf(date, sessions, zone) }
        val exercise = activityDao.getExerciseBetween(start, end, userId)
        val active = activeSpans(userId, start, end, sleepRows, exercise.map { it.startUtc to it.endUtc })
        val boluses = bolusDao.getBolusBetween(start, end - 1, userId).groupBy { dayOf(it.timestampUtc) }
            .mapValues { (_, list) -> list.sumOf { it.value.toDouble() } }
        val meals = mealDao.getMealsBetween(start, end, userId).filter { it.carbohydrates > 0 || (it.calories ?: 0) > 0 }
            .groupBy { dayOf(it.mealTimeUtc) }
        val basalSegments = chunks(first, lastExclusive).flatMap { (from, to) ->
            val s = startOf(from); val e = minOf(startOf(to), now)
            if (e > s) getBasalTimeline(s, e, userId) else emptyList()
        }

        generateSequence(first) { it.plusDays(1) }.takeWhile { it < lastExclusive }.map { date ->
            val key = date.toEpochDay()
            val dayMeals = meals[date].orEmpty()
            val dayStart = startOf(date); val dayEnd = startOf(date.plusDays(1))
            StatsDay(
                date = date,
                steps = steps[key]?.toInt()?.takeIf { it > 0 },
                avgBpm = heartRate[key],
                restingBpm = resting[key],
                hrvMs = hrv[key],
                sleep = nights[date],
                activeMinutes = active.clippedMinutes(dayStart, dayEnd),
                totalKcal = totalKcal[key],
                activeKcal = activeKcal[key],
                carbs = dayMeals.takeIf { it.isNotEmpty() }?.sumOf { it.carbohydrates.toDouble() },
                protein = dayMeals.takeIf { it.isNotEmpty() }?.sumOf { (it.proteins ?: 0).toDouble() },
                fat = dayMeals.takeIf { it.isNotEmpty() }?.sumOf { (it.fats ?: 0).toDouble() },
                mealKcal = dayMeals.takeIf { it.isNotEmpty() }?.sumOf { it.kcal() },
                glucose = glucose[key],
                bolusUnits = boluses[date],
                basalUnits = basalSegments.deliveredUnits(dayStart, minOf(dayEnd, now)).takeIf { it > 0.0 }
            )
        }.toList()
    }

    /** Raised-activity ranges (from heart rate and steps) plus workouts, as sorted spans. */
    private suspend fun activeSpans(
        userId: Int, start: Long, end: Long,
        sleepRows: List<uk.scimone.diafit.core.domain.model.SleepStageEntity>,
        workouts: List<Pair<Long, Long>>
    ): List<Pair<Long, Long>> {
        val hr = activityDao.getHeartRateBucketsBetween(start, end, userId)
        val stepRows = activityDao.getStepsBetween(start, end, userId)
        val sleep = ActivityData(sleep = sleepRows).sleepSessions
        val elevated = ElevatedActivity.detect(hr, stepRows, sleep).map { it.startUtc to it.endUtc }
        return (elevated + workouts).sortedBy { it.first }
    }

    /** Minutes of the (overlapping-allowed) [spans] inside the day, each minute counted once; null when none touches it. */
    private fun List<Pair<Long, Long>>.clippedMinutes(dayStart: Long, dayEnd: Long): Double? {
        val inDay = mapNotNull { (s, e) ->
            val cs = maxOf(s, dayStart); val ce = minOf(e, dayEnd)
            if (ce > cs) cs to ce else null
        }
        return inDay.takeIf { it.isNotEmpty() }?.let { unionDurationMs(it) / 60_000.0 }
    }

    private fun nightOf(date: LocalDate, sessions: List<SleepSession>, zone: ZoneId): SleepNight {
        val ms = HashMap<SleepStage, Long>()
        sessions.flatMap { it.stages }.forEach { ms.merge(it.sleepStage, it.endUtc - it.startUtc, Long::plus) }
        // The schedule follows the longest session, so a nap doesn't move bedtime to the afternoon.
        val main = sessions.maxBy { it.asleepMs }
        fun axis(utc: Long, anchor: LocalDate): Int {
            val axisStart = anchor.minusDays(1).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
            return ((utc - axisStart) / 60_000L).toInt().coerceIn(0, 36 * 60)
        }
        return SleepNight(
            deepMs = ms[SleepStage.DEEP] ?: 0,
            remMs = ms[SleepStage.REM] ?: 0,
            lightMs = (ms[SleepStage.LIGHT] ?: 0) + (ms[SleepStage.SLEEPING] ?: 0),
            awakeMs = ms[SleepStage.AWAKE] ?: 0,
            bedMinutes = axis(main.startUtc, date),
            wakeMinutes = axis(main.endUtc, date),
            startUtc = main.startUtc,
            endUtc = main.endUtc,
            spans = main.stages.sortedBy { it.startUtc }.map { SleepSpan(it.startUtc, it.endUtc, it.sleepStage) }
        )
    }

    private fun MealEntity.kcal(): Double {
        val given = calories ?: 0
        return if (given > 0) given.toDouble() else carbohydrates * 4.0 + (proteins ?: 0) * 4.0 + (fats ?: 0) * 9.0
    }

    /** Date ranges of at most a month, so the basal timeline (one event per temp basal) is never built for a whole year at once. */
    private fun chunks(first: LocalDate, lastExclusive: LocalDate): List<Pair<LocalDate, LocalDate>> =
        generateSequence(first) { it.plusDays(CHUNK_DAYS) }.takeWhile { it < lastExclusive }
            .map { it to minOf(it.plusDays(CHUNK_DAYS), lastExclusive) }.toList()

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val CHUNK_DAYS = 31L
    }
}

/** [startMs, endMs) split where the zone's UTC offset changes (daylight saving), each with its offset in ms. */
internal fun offsetSegments(zone: ZoneId, startMs: Long, endMs: Long): List<Triple<Long, Long, Long>> {
    val rules = zone.rules
    val out = mutableListOf<Triple<Long, Long, Long>>()
    var s = startMs
    while (s < endMs) {
        val instant = Instant.ofEpochMilli(s)
        val offset = rules.getOffset(instant).totalSeconds * 1000L
        val next = rules.nextTransition(instant)?.instant?.toEpochMilli() ?: Long.MAX_VALUE
        val e = minOf(endMs, next)
        out += Triple(s, e, offset)
        s = e
    }
    return out
}
