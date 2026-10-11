package uk.scimone.diafit.patterns.domain

import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.MealSitting
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val BIN_MINUTES = 5
const val BINS_PER_DAY = 24 * 60 / BIN_MINUTES
const val BINS_PER_HOUR = 60 / BIN_MINUTES

/**
 * One local day of glucose as 288 five-minute means (mg/dL), NaN where nothing was recorded. Binning makes 1-minute
 * and 5-minute sources count alike and keeps "how much of the window was measured" meaningful.
 */
class DayGlucose(val date: LocalDate, val bins: FloatArray) {
    init { require(bins.size == BINS_PER_DAY) }

    fun coverage(r: IntRange): Double = r.count { !bins[it].isNaN() }.toDouble() / r.count()

    fun count(r: IntRange, predicate: (Float) -> Boolean): Int = r.count { !bins[it].isNaN() && predicate(bins[it]) }

    /** Mean of the recorded bins in [r], or NaN when fewer than [minBins] were recorded. */
    fun mean(r: IntRange, minBins: Int = 1): Float {
        var sum = 0f; var n = 0
        for (i in r) if (!bins[i].isNaN()) { sum += bins[i]; n++ }
        return if (n >= minBins) sum / n else Float.NaN
    }

    companion object {
        /** Groups [readings] by local day in [zone]. Days are returned oldest first. */
        fun from(readings: List<CgmEntity>, zone: ZoneId): List<DayGlucose> {
            val sums = HashMap<LocalDate, FloatArray>()
            val counts = HashMap<LocalDate, IntArray>()
            for (r in readings) {
                if (r.valueMgdl <= 0) continue
                val local = Instant.ofEpochMilli(r.timestamp).atZone(zone)
                val bin = ((local.hour * 60 + local.minute) / BIN_MINUTES).coerceIn(0, BINS_PER_DAY - 1)
                sums.getOrPut(local.toLocalDate()) { FloatArray(BINS_PER_DAY) }[bin] += r.valueMgdl.toFloat()
                counts.getOrPut(local.toLocalDate()) { IntArray(BINS_PER_DAY) }[bin]++
            }
            return sums.keys.sorted().map { date ->
                val s = sums.getValue(date); val c = counts.getValue(date)
                DayGlucose(date, FloatArray(BINS_PER_DAY) { if (c[it] == 0) Float.NaN else s[it] / c[it] })
            }
        }
    }
}

/** What the detectors work from. [days] may reach further back than the detection windows. */
class DetectionInput(
    val days: List<DayGlucose>,
    val thresholds: GlucoseThresholds,
    val nowUtc: Long,
    val zone: ZoneId,
    /** Meals as the user thinks of them (courses grouped), for the meal detectors. */
    val sittings: List<MealSitting> = emptyList(),
    val boluses: List<BolusEntity> = emptyList(),
    /** Workouts and stretches of raised activity, for the exercise detectors. */
    val events: List<ActivityEvent> = emptyList()
) {
    val today: LocalDate get() = Instant.ofEpochMilli(nowUtc).atZone(zone).toLocalDate()

    /** The days of the most recent [n] calendar days including today. */
    fun lastDays(n: Int): List<DayGlucose> = days.filter { it.date > today.minusDays(n.toLong()) && it.date <= today }

    fun startOfDayUtc(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
}

/** Glucose at an arbitrary instant, from the same five-minute bins. NaN where nothing was recorded. */
class GlucoseLookup(days: List<DayGlucose>, private val zone: ZoneId) {
    private val byDate = days.associateBy { it.date }

    fun at(utcMs: Long): Float {
        val local = Instant.ofEpochMilli(utcMs).atZone(zone)
        val day = byDate[local.toLocalDate()] ?: return Float.NaN
        return day.bins[((local.hour * 60 + local.minute) / BIN_MINUTES).coerceIn(0, BINS_PER_DAY - 1)]
    }
}
