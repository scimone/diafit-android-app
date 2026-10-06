package uk.scimone.diafit.history.presentation.model

import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.history.domain.model.DayHistory
import uk.scimone.diafit.history.domain.model.GlucoseThresholds
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
    val timeInRangePercent: Int?
) {
    val totalCarbs: Float get() = carbs.sumOf { it.total.toDouble() }.toFloat()
    val totalInsulin: Float get() = insulin.sumOf { it.total.toDouble() }.toFloat()
}

fun DayHistory.toUi(
    target: GlucoseTargetRange,
    cluster: ClusterTreatmentsUseCase,
    zone: ZoneId = ZoneId.systemDefault()
): DayHistoryUi {
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val points = readings.map { GlucosePoint(it.timestamp, it.valueMgdl) }
    val inRange = points.count { it.mgdl in target.lowerBound..target.upperBound }
    return DayHistoryUi(
        epochDay = date.toEpochDay(),
        weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
        dayOfMonth = date.format(DateTimeFormatter.ofPattern("d", Locale.getDefault())),
        dayStartUtc = start,
        dayEndUtc = end,
        glucose = points,
        carbs = cluster(meals.map { TreatmentEvent(it.mealTimeUtc, it.carbohydrates.toFloat()) }),
        insulin = cluster(boluses.map { TreatmentEvent(it.timestampUtc, it.value) }),
        timeInRangePercent = if (points.isEmpty()) null else inRange * 100 / points.size
    )
}

/** Glucose thresholds the charts are drawn against. */
fun GlucoseTargetRange.toThresholds() = GlucoseThresholds.from(this)
