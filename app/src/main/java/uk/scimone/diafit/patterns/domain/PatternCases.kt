package uk.scimone.diafit.patterns.domain

import java.time.LocalDate
import kotlin.math.abs

/** Builders for [PatternExample]s, shared by the detectors. */
internal object Cases {
    private const val MIN = 60_000L
    private const val BIN_MS = BIN_MINUTES * MIN

    /** Up to this many examples for a single-group finding; comparisons show [PER_GROUP] from each group. */
    const val SINGLE = 3
    const val PER_GROUP = 2

    /**
     * A window [focus] (5-minute bins) of [date], drawn with [beforeMin] of lead-in (to show what came before, often the
     * likely reason) and [afterMin] after it. [markBin] is a bin of the same day.
     */
    fun window(
        input: DetectionInput, date: LocalDate, focus: IntRange, beforeMin: Int, afterMin: Int, note: String,
        markBin: Int? = null, group: String? = null
    ): PatternExample {
        val day0 = input.startOfDayUtc(date)
        val f0 = day0 + focus.first * BIN_MS
        val f1 = day0 + (focus.last + 1) * BIN_MS
        return PatternExample(
            fromUtc = f0 - beforeMin * MIN, toUtc = f1 + afterMin * MIN, focusFromUtc = f0, focusToUtc = f1, note = note,
            markUtc = markBin?.let { day0 + it * BIN_MS + BIN_MS / 2 }, group = group
        )
    }

    /** A whole local day. */
    fun day(input: DetectionInput, date: LocalDate, note: String, markBin: Int? = null, group: String? = null) =
        window(input, date, 0 until BINS_PER_DAY, 0, 0, note, markBin, group)

    /**
     * Around an event starting at [startUtc] (and lasting until [endUtc]): drawn from [beforeMin] before the start to
     * [afterMin] after the end; the focus is [focusFromMin]..[focusToMin] minutes after the start.
     */
    fun event(
        startUtc: Long, endUtc: Long, label: String?, beforeMin: Int, afterMin: Int, focusFromMin: Int, focusToMin: Int,
        note: String, markUtc: Long? = null, group: String? = null
    ) = PatternExample(
        fromUtc = startUtc - beforeMin * MIN, toUtc = endUtc + afterMin * MIN,
        focusFromUtc = startUtc + focusFromMin * MIN, focusToUtc = startUtc + focusToMin * MIN, note = note,
        eventUtc = startUtc, eventEndUtc = endUtc.takeIf { it > startUtc }, eventLabel = label, markUtc = markUtc, group = group
    )

    /** The [n] members of [items] closest to the group's median [value]: typical examples, not the extremes. */
    fun <T> typical(items: List<T>, n: Int = PER_GROUP, value: (T) -> Double): List<T> {
        if (items.isEmpty()) return emptyList()
        val sorted = items.map(value).sorted()
        val med = sorted[sorted.size / 2]
        return items.sortedBy { abs(value(it) - med) }.take(n)
    }

    /** Lowest (or highest) recorded bin of [day] in [r], with its value; null when nothing was recorded. */
    fun extreme(day: DayGlucose, r: IntRange, lowest: Boolean): Pair<Int, Float>? {
        val rec = r.filter { !day.bins[it].isNaN() }
        val bin = (if (lowest) rec.minByOrNull { day.bins[it] } else rec.maxByOrNull { day.bins[it] }) ?: return null
        return bin to day.bins[bin]
    }

    fun clock(bin: Int) = "%02d:%02d".format(bin * BIN_MINUTES / 60 % 24, bin * BIN_MINUTES % 60)

    fun clock(utc: Long, zone: java.time.ZoneId): String =
        java.time.Instant.ofEpochMilli(utc).atZone(zone).let { "%02d:%02d".format(it.hour, it.minute) }

    fun duration(min: Int) = if (min >= 60) "${min / 60} h ${"%02d".format(min % 60)} min" else "$min min"

    /** "+42" / "−18" / "0". */
    fun signed(v: Number): String = Math.round(v.toDouble()).toInt().let { if (it > 0) "+$it" else if (it < 0) "−${-it}" else "0" }
}
