package uk.scimone.diafit.core.domain.model

/**
 * Per-local-day aggregates read straight from SQL (`day` = the local epoch day), so months of per-minute data
 * never have to be loaded into memory.
 */
data class GlucoseDayRow(
    val day: Long,
    val n: Int,
    val sum: Long,
    val sumSq: Long,
    /** Readings below the "very low" limit / below the low limit (incl. very low) / above the high limit (incl. very high) / above "very high". */
    val veryLow: Int,
    val low: Int,
    val high: Int,
    val veryHigh: Int
)

data class HeartRateDayRow(val day: Long, val avg: Double, val n: Int)

data class StepsDayRow(val day: Long, val total: Long)

data class MetricDayRow(val day: Long, val sum: Double, val avg: Double, val n: Int)
