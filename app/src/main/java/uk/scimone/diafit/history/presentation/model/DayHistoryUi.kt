package uk.scimone.diafit.history.presentation.model

import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.ActivityDayStats
import uk.scimone.diafit.core.domain.model.ActivitySpan
import uk.scimone.diafit.core.domain.model.DayGlucoseStats
import uk.scimone.diafit.core.domain.model.GlucoseSample
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.history.domain.model.DayHistory
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.domain.model.TreatmentCluster
import uk.scimone.diafit.history.domain.model.TreatmentEvent
import uk.scimone.diafit.history.domain.usecase.ClusterTreatmentsUseCase
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class GlucosePoint(val timeUtc: Long, val mgdl: Int)

/** One day, ready to draw: the time window, glucose trace and clustered treatments. */
data class DayHistoryUi(
    val epochDay: Long,
    val weekday: String,
    val dayOfMonth: String,
    val dayStartUtc: Long,
    val dayEndUtc: Long,
    val glucose: List<GlucosePoint>,
    val carbs: List<TreatmentCluster>,
    val insulin: List<TreatmentCluster>,
    /** SMB insulin of the day: not in the strip, but part of [totalInsulin]. */
    val smbUnits: Float = 0f,
    /** Sleep and exercise overlapping the day. */
    val activity: ActivityData,
    val elevatedActivity: List<ActivitySpan>,
    /** Steps, sleep (the night that ended this day) and workouts of the day; null when there is none. */
    val activityStats: ActivityDayStats?,
    /** Null when the day has no readings. */
    val stats: DayGlucoseStats?
) {
    val totalCarbs: Float get() = carbs.sumOf { it.total.toDouble() }.toFloat()
    val totalInsulin: Float get() = insulin.sumOf { it.total.toDouble() }.toFloat() + smbUnits
    val steps: Int get() = activityStats?.steps ?: 0
    val sleepMs: Long get() = activityStats?.sleepMs ?: 0L
    /** Time in elevated activity or logged workouts (overlap counted once). */
    val activeMs: Long
        get() = uk.scimone.diafit.core.domain.model.unionDurationMs(
            elevatedActivity.map { maxOf(it.startUtc, dayStartUtc) to minOf(it.endUtc, dayEndUtc) } +
                activity.exercise.map { maxOf(it.startUtc, dayStartUtc) to minOf(it.endUtc, dayEndUtc) }
        )
}

fun DayHistory.toUi(
    target: GlucoseTargetRange,
    cluster: ClusterTreatmentsUseCase,
    zone: ZoneId = ZoneId.systemDefault()
): DayHistoryUi {
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val points = readings.map { GlucosePoint(it.timestamp, it.valueMgdl) }
    return DayHistoryUi(
        epochDay = date.toEpochDay(),
        weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
        dayOfMonth = date.format(DateTimeFormatter.ofPattern("d", Locale.getDefault())),
        dayStartUtc = start,
        dayEndUtc = end,
        glucose = points,
        carbs = cluster(meals.map { TreatmentEvent(it.mealTimeUtc, it.carbohydrates.toFloat()) }),
        insulin = cluster(boluses.filter { !it.isSmb }.map { TreatmentEvent(it.timestampUtc, it.value) }),
        smbUnits = boluses.filter { it.isSmb }.sumOf { it.value.toDouble() }.toFloat(),
        activity = activity,
        elevatedActivity = elevatedActivity,
        activityStats = ActivityDayStats.from(activity, start, end),
        stats = DayGlucoseStats.from(
            readings.sortedBy { it.timestamp }.map { GlucoseSample(it.timestamp, it.valueMgdl) },
            GlucoseThresholds.from(target)
        )
    )
}

/** Glucose thresholds the charts are drawn against. */
fun GlucoseTargetRange.toThresholds() = GlucoseThresholds.from(this)
