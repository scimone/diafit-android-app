package uk.scimone.diafit.insights.domain

import uk.scimone.diafit.patterns.domain.PatternAgp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Category A: recurring glucose patterns tied to the time of day, plus weekday vs weekend. Works on whole days:
 * a pattern needs enough days that measured the window (coverage), a minimum number of days showing it, a minimum
 * size, and has to show up in both halves of the period (stability). Candidate windows are six 4-hour blocks;
 * neighbouring blocks that qualify together are re-tested as one window. That keeps the number of comparisons
 * small, and the size/consistency thresholds (not a p-value) are what filter chance findings.
 */
object TimeOfDayDetector {
    const val WINDOW_DAYS = 14
    const val WEEKEND_WINDOW_DAYS = 28

    private const val BLOCK_HOURS = 4
    private const val BLOCKS = 24 / BLOCK_HOURS
    private const val BLOCK_BINS = BLOCK_HOURS * BINS_PER_HOUR

    /** A day counts for a window only if at least this much of it was measured. */
    private const val MIN_COVERAGE = 0.7
    private const val MIN_VALID_DAYS = 7
    private const val MIN_VALID_DAYS_STEADY = 10

    private const val LOW_BINS_FOR_A_LOW = 3 // 15 minutes
    private const val SEVERE_BINS = 2
    private const val MIN_LOW_DAYS = 3
    private const val MIN_LOW_SHARE = 0.20

    private const val HIGH_DAY_SHARE_OF_WINDOW = 0.25
    private const val MIN_HIGH_DAY_SHARE = 0.40
    private const val MIN_WINDOW_TAR = 0.25

    private const val MIN_CV = 36.0
    private const val CV_ABOVE_OVERALL = 5.0
    private const val MIN_SD = 30.0

    private const val DAWN_RISE = 20.0
    private const val DAWN_MIN_END_LEVEL = 110.0
    private const val DAWN_MIN_DAY_SHARE = 0.6

    private const val STEADY_MIN_TIR = 0.90
    private const val STEADY_MAX_CV = 28.0
    private const val STEADY_MAX_TBR = 0.01

    private const val WEEKEND_MIN_DIFF = 10.0
    private const val WEEKEND_MIN_SE_FACTOR = 2.0
    private const val WEEKEND_MIN_WEEKEND_DAYS = 6
    private const val WEEKEND_MIN_WEEKDAYS = 12

    fun detect(input: DetectionInput): List<InsightCandidate> {
        val days = input.lastDays(WINDOW_DAYS)
        val out = ArrayList<InsightCandidate>()
        val ctx = Ctx(input, days)
        if (days.size >= MIN_VALID_DAYS && ctx.agp != null) {
            out += lows(ctx)
            out += highs(ctx)
            out += variability(ctx)
            out += dawn(ctx)
            out += steady(ctx)
        }
        out += weekend(input)
        return out
    }

    // ---------------------------------------------------------------------------------------------------------

    private class Ctx(val input: DetectionInput, val days: List<DayGlucose>) {
        val t = input.thresholds
        val agp: PatternAgp? by lazy { agpOf(days) }
        val fromUtc = input.startOfDayUtc(input.today.minusDays((WINDOW_DAYS - 1).toLong()))
        val overallCv: Double by lazy {
            val v = days.flatMap { d -> d.bins.filter { !it.isNaN() } }
            cv(v)
        }

        fun valid(r: IntRange) = days.filter { it.coverage(r) >= MIN_COVERAGE }

        /** First and second half of [valid] by date, for the stability check. */
        fun halves(valid: List<DayGlucose>): Pair<List<DayGlucose>, List<DayGlucose>> =
            valid.take(valid.size / 2) to valid.drop(valid.size / 2)

        fun graph(r: IntRange): MiniGraph? = agp?.toGraph(listOf(spanOf(r)))
    }

    private fun spanOf(r: IntRange) = HourSpan(r.first / BINS_PER_HOUR, (r.last + 1) / BINS_PER_HOUR)

    private fun blockRange(b: Int) = (b * BLOCK_BINS) until ((b + 1) * BLOCK_BINS)

    /** Evaluates each block; consecutive qualifying blocks are re-evaluated as one window, falling back to the blocks. */
    private fun <T> scanBlocks(eval: (IntRange) -> T?): List<Pair<IntRange, T>> {
        val per = (0 until BLOCKS).map { eval(blockRange(it)) }
        val out = ArrayList<Pair<IntRange, T>>()
        var i = 0
        while (i < BLOCKS) {
            if (per[i] == null) { i++; continue }
            var j = i
            while (j + 1 < BLOCKS && per[j + 1] != null) j++
            if (j > i) {
                val merged = (i * BLOCK_BINS) until ((j + 1) * BLOCK_BINS)
                val m = eval(merged)
                if (m != null) { out += merged to m; i = j + 1; continue }
            }
            for (k in i..j) out += blockRange(k) to per[k]!!
            i = j + 1
        }
        return out
    }

    private fun isNight(r: IntRange) = r.last < 8 * BINS_PER_HOUR || r.first >= 22 * BINS_PER_HOUR

    private fun title(prefix: String, r: IntRange) = "$prefix ${spanOf(r).label.replace("–", " and ")}"

    private fun id(kind: String, r: IntRange) = "tod.$kind.%02d-%02d".format(r.first / BINS_PER_HOUR, (r.last + 1) / BINS_PER_HOUR % 24)

    private fun timeLabel(bin: Int) = "%02d:%02d".format(bin * BIN_MINUTES / 60, bin * BIN_MINUTES % 60)

    private fun dayLabel(d: DayGlucose) =
        "${d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${d.date.dayOfMonth} ${d.date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"

    private fun share(n: Int, d: Int) = if (d == 0) 0.0 else n.toDouble() / d

    private fun pct(x: Double) = "${(x * 100).roundToInt()} %"

    private fun cv(values: List<Float>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        if (mean <= 0) return 0.0
        return sd(values) / mean * 100
    }

    private fun sd(values: List<Float>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / (values.size - 1))
    }

    // ---------------------------------------------------------------------------------------------------------

    private class LowFinding(val valid: List<DayGlucose>, val lowDays: List<DayGlucose>, val severeDays: Int)

    private fun lows(c: Ctx): List<InsightCandidate> {
        fun eval(r: IntRange): LowFinding? {
            val valid = c.valid(r)
            if (valid.size < MIN_VALID_DAYS) return null
            val hasLow = { d: DayGlucose -> d.count(r) { it < c.t.low } >= LOW_BINS_FOR_A_LOW }
            val lowDays = valid.filter(hasLow)
            if (lowDays.size < MIN_LOW_DAYS || share(lowDays.size, valid.size) < MIN_LOW_SHARE) return null
            val (a, b) = c.halves(valid)
            if (a.none(hasLow) || b.none(hasLow)) return null
            return LowFinding(valid, lowDays, valid.count { d -> d.count(r) { it < c.t.veryLow } >= SEVERE_BINS })
        }
        return scanBlocks(::eval).mapNotNull { (r, f) ->
            val graph = c.graph(r) ?: return@mapNotNull null
            val severe = f.severeDays >= 2
            val s = share(f.lowDays.size, f.valid.size)
            val examples = f.lowDays.sortedBy { d -> r.filter { !d.bins[it].isNaN() }.minOf { d.bins[it] } }.take(3).map { d ->
                val bin = r.filter { !d.bins[it].isNaN() }.minBy { d.bins[it] }
                "${dayLabel(d)} · lowest ${d.bins[bin].roundToInt()} mg/dL at ${timeLabel(bin)}"
            }
            InsightCandidate(
                card = InsightCard(
                    id = id("low", r), category = InsightCategory.TIME_OF_DAY, detector = "low",
                    title = title("Low glucose between", r),
                    summary = "Glucose dropped below ${c.t.low} mg/dL for at least 15 minutes on ${f.lowDays.size} of ${f.valid.size} days" +
                        " in this window" + if (severe) ", and below ${c.t.veryLow} mg/dL on ${f.severeDays}." else ".",
                    miniGraph = graph, observationFromUtc = c.fromUtc, observationToUtc = c.input.nowUtc,
                    sampleSize = f.valid.size, sampleUnit = "days", positiveCount = f.lowDays.size,
                    effectSize = s, effectLabel = "${f.lowDays.size} of ${f.valid.size} days (${pct(s)})",
                    uncertainty = "Seen in both the first and the second half of the period.",
                    limitations = listOf(
                        "Only days with at least ${pct(MIN_COVERAGE)} of this window measured are counted.",
                        "Meals, insulin and activity from earlier in the day aren't part of this check, so the reason isn't known."
                    ),
                    examples = examples, tone = InsightTone.UNFAVORABLE,
                    priority = rank(if (severe) 100.0 else 85.0, (s / 0.5).coerceIn(0.0, 1.0), f.valid.size)
                ),
                topic = if (isNight(r)) TipTopic.LOW_NIGHT else TipTopic.LOW_DAY,
                escalate = severe || s >= 0.4
            )
        }
    }

    private class HighFinding(val valid: List<DayGlucose>, val highDays: List<DayGlucose>, val tar: Double, val veryHighShare: Double)

    private fun highs(c: Ctx): List<InsightCandidate> {
        fun eval(r: IntRange): HighFinding? {
            val valid = c.valid(r)
            if (valid.size < MIN_VALID_DAYS) return null
            val isHighDay = { d: DayGlucose -> share(d.count(r) { it > c.t.high }, r.count()) >= HIGH_DAY_SHARE_OF_WINDOW }
            val highDays = valid.filter(isHighDay)
            val tar = share(valid.sumOf { d -> d.count(r) { it > c.t.high } }, valid.sumOf { d -> d.count(r) { true } })
            if (tar < MIN_WINDOW_TAR || share(highDays.size, valid.size) < MIN_HIGH_DAY_SHARE) return null
            val (a, b) = c.halves(valid)
            if (share(a.count(isHighDay), a.size) < 0.2 || share(b.count(isHighDay), b.size) < 0.2) return null
            val vh = share(valid.sumOf { d -> d.count(r) { it > c.t.veryHigh } }, valid.sumOf { d -> d.count(r) { true } })
            return HighFinding(valid, highDays, tar, vh)
        }
        return scanBlocks(::eval).mapNotNull { (r, f) ->
            val graph = c.graph(r) ?: return@mapNotNull null
            val veryHigh = f.veryHighShare >= 0.05
            val examples = f.highDays.sortedByDescending { d -> r.filter { !d.bins[it].isNaN() }.maxOf { d.bins[it] } }.take(3).map { d ->
                val bin = r.filter { !d.bins[it].isNaN() }.maxBy { d.bins[it] }
                "${dayLabel(d)} · peak ${d.bins[bin].roundToInt()} mg/dL at ${timeLabel(bin)}"
            }
            InsightCandidate(
                card = InsightCard(
                    id = id("high", r), category = InsightCategory.TIME_OF_DAY, detector = "high",
                    title = title("High glucose between", r),
                    summary = "Glucose was above ${c.t.high} mg/dL ${pct(f.tar)} of the time in this window, and for a quarter of it or more on ${f.highDays.size} of ${f.valid.size} days.",
                    miniGraph = graph, observationFromUtc = c.fromUtc, observationToUtc = c.input.nowUtc,
                    sampleSize = f.valid.size, sampleUnit = "days", positiveCount = f.highDays.size,
                    effectSize = f.tar, effectLabel = "${pct(f.tar)} of the time above ${c.t.high} mg/dL",
                    uncertainty = "Seen in both the first and the second half of the period.",
                    limitations = listOf(
                        "Only days with at least ${pct(MIN_COVERAGE)} of this window measured are counted.",
                        "Meals, insulin and activity aren't part of this check, so the reason isn't known."
                    ),
                    examples = examples, tone = InsightTone.UNFAVORABLE,
                    priority = rank(if (veryHigh) 75.0 else 60.0, (f.tar / 0.6).coerceIn(0.0, 1.0), f.valid.size)
                ),
                topic = if (isNight(r)) TipTopic.HIGH_NIGHT else TipTopic.HIGH_DAY
            )
        }
    }

    private class VarFinding(val valid: List<DayGlucose>, val cv: Double)

    private fun variability(c: Ctx): List<InsightCandidate> {
        fun values(ds: List<DayGlucose>, r: IntRange) = ds.flatMap { d -> r.map { d.bins[it] }.filter { !it.isNaN() } }
        fun eval(r: IntRange): VarFinding? {
            val valid = c.valid(r)
            if (valid.size < MIN_VALID_DAYS) return null
            val v = values(valid, r)
            val cv = cv(v)
            if (cv < MIN_CV || cv < c.overallCv + CV_ABOVE_OVERALL || sd(v) < MIN_SD) return null
            val (a, b) = c.halves(valid)
            if (cv(values(a, r)) < MIN_CV - 6 || cv(values(b, r)) < MIN_CV - 6) return null
            return VarFinding(valid, cv)
        }
        return scanBlocks(::eval).mapNotNull { (r, f) ->
            val graph = c.graph(r) ?: return@mapNotNull null
            InsightCandidate(
                card = InsightCard(
                    id = id("var", r), category = InsightCategory.TIME_OF_DAY, detector = "variability",
                    title = title("Glucose varies a lot between", r),
                    summary = "In this window glucose swings more than at other times of the day (variability ${f.cv.roundToInt()} % compared with ${c.overallCv.roundToInt()} % overall).",
                    miniGraph = graph, observationFromUtc = c.fromUtc, observationToUtc = c.input.nowUtc,
                    sampleSize = f.valid.size, sampleUnit = "days",
                    effectSize = f.cv, effectLabel = "variability ${f.cv.roundToInt()} % (overall ${c.overallCv.roundToInt()} %)",
                    uncertainty = "Present in both the first and the second half of the period.",
                    limitations = listOf(
                        "Variability here includes day-to-day differences, not only swings within a day.",
                        "Meals, insulin and activity aren't part of this check, so the reason isn't known."
                    ),
                    tone = InsightTone.UNFAVORABLE,
                    priority = rank(40.0, ((f.cv - MIN_CV) / 20.0).coerceIn(0.0, 1.0), f.valid.size)
                ),
                topic = TipTopic.VARIABILITY
            )
        }
    }

    private fun dawn(c: Ctx): List<InsightCandidate> {
        val before = (3 * BINS_PER_HOUR) until (3 * BINS_PER_HOUR + 6)      // 03:00–03:30
        val after = (6 * BINS_PER_HOUR + 6) until (7 * BINS_PER_HOUR)       // 06:30–07:00
        val rises = c.days.mapNotNull { d ->
            val a = d.mean(before, 4); val b = d.mean(after, 4)
            if (a.isNaN() || b.isNaN()) null else d to (b - a) to b
        }
        if (rises.size < MIN_VALID_DAYS) return emptyList()
        val rising = rises.filter { it.first.second > DAWN_RISE }
        val medianRise = rises.map { it.first.second }.sorted()[rises.size / 2].toDouble()
        val endLevel = rises.map { it.second }.sorted()[rises.size / 2].toDouble()
        if (share(rising.size, rises.size) < DAWN_MIN_DAY_SHARE || medianRise < DAWN_RISE || endLevel < DAWN_MIN_END_LEVEL) return emptyList()
        val graph = c.agp?.toGraph(listOf(HourSpan(3, 7))) ?: return emptyList()
        val examples = rising.sortedByDescending { it.first.second }.take(3).map { (p, _) ->
            "${dayLabel(p.first)} · up ${p.second.roundToInt()} mg/dL between 03:00 and 07:00"
        }
        return listOf(
            InsightCandidate(
                card = InsightCard(
                    id = "tod.dawn", category = InsightCategory.TIME_OF_DAY, detector = "dawn",
                    title = "Glucose rises in the early morning",
                    summary = "Between about 03:00 and 07:00 glucose climbed by a typical ${medianRise.roundToInt()} mg/dL, on ${rising.size} of ${rises.size} days.",
                    miniGraph = graph, observationFromUtc = c.fromUtc, observationToUtc = c.input.nowUtc,
                    sampleSize = rises.size, sampleUnit = "days", positiveCount = rising.size,
                    effectSize = medianRise, effectLabel = "+${medianRise.roundToInt()} mg/dL (median)",
                    uncertainty = "The rise appeared on ${pct(share(rising.size, rises.size))} of days.",
                    limitations = listOf(
                        "Compares 03:00–03:30 with 06:30–07:00 only, and ends at a typical ${endLevel.roundToInt()} mg/dL.",
                        "Overnight insulin, late meals and lows earlier in the night can also shape this curve."
                    ),
                    examples = examples, tone = InsightTone.UNFAVORABLE,
                    priority = rank(45.0, ((medianRise - DAWN_RISE) / 40.0).coerceIn(0.0, 1.0), rises.size)
                ),
                topic = TipTopic.DAWN
            )
        )
    }

    private class SteadyFinding(val valid: List<DayGlucose>, val tir: Double, val cv: Double)

    private fun steady(c: Ctx): List<InsightCandidate> {
        fun eval(r: IntRange): SteadyFinding? {
            val valid = c.valid(r)
            if (valid.size < MIN_VALID_DAYS_STEADY) return null
            val v = valid.flatMap { d -> r.map { d.bins[it] }.filter { !it.isNaN() } }
            val tir = share(v.count { it >= c.t.low && it <= c.t.high }, v.size)
            val tbr = share(v.count { it < c.t.low }, v.size)
            val cv = cv(v)
            return if (tir >= STEADY_MIN_TIR && tbr <= STEADY_MAX_TBR && cv <= STEADY_MAX_CV) SteadyFinding(valid, tir, cv) else null
        }
        return scanBlocks(::eval).sortedByDescending { it.second.tir }.take(2).mapNotNull { (r, f) ->
            val graph = c.graph(r) ?: return@mapNotNull null
            InsightCandidate(
                card = InsightCard(
                    id = id("steady", r), category = InsightCategory.TIME_OF_DAY, detector = "steady",
                    title = title("Steady glucose between", r),
                    summary = "Glucose stayed between ${c.t.low} and ${c.t.high} mg/dL ${pct(f.tir)} of the time in this window over ${f.valid.size} days.",
                    miniGraph = graph, observationFromUtc = c.fromUtc, observationToUtc = c.input.nowUtc,
                    sampleSize = f.valid.size, sampleUnit = "days",
                    effectSize = f.tir, effectLabel = "${pct(f.tir)} in range",
                    uncertainty = "Consistent across the period (variability ${f.cv.roundToInt()} %).",
                    limitations = listOf("Only days with at least ${pct(MIN_COVERAGE)} of this window measured are counted."),
                    tone = InsightTone.FAVORABLE,
                    priority = rank(30.0, ((f.tir - STEADY_MIN_TIR) / 0.1).coerceIn(0.0, 1.0), f.valid.size)
                ),
                topic = if (isNight(r)) TipTopic.STEADY_NIGHT else TipTopic.STEADY_DAY
            )
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    private fun weekend(input: DetectionInput): List<InsightCandidate> {
        val t = input.thresholds
        val whole = 0 until BINS_PER_DAY
        val days = input.lastDays(WEEKEND_WINDOW_DAYS).filter { it.coverage(whole) >= MIN_COVERAGE }
        fun isWeekend(d: DayGlucose) = d.date.dayOfWeek == DayOfWeek.SATURDAY || d.date.dayOfWeek == DayOfWeek.SUNDAY
        val weekend = days.filter(::isWeekend)
        val weekdays = days.filterNot(::isWeekend)
        if (weekend.size < WEEKEND_MIN_WEEKEND_DAYS || weekdays.size < WEEKEND_MIN_WEEKDAYS) return emptyList()
        fun tir(d: DayGlucose): Double {
            val v = d.bins.filter { !it.isNaN() }
            return share(v.count { it >= t.low && it <= t.high }, v.size) * 100
        }
        val a = weekend.map(::tir); val b = weekdays.map(::tir)
        fun variance(x: List<Double>) = x.sumOf { (it - x.average()).let { d -> d * d } } / (x.size - 1)
        val diff = a.average() - b.average()
        val se = sqrt(variance(a) / a.size + variance(b) / b.size)
        if (abs(diff) < WEEKEND_MIN_DIFF || abs(diff) < WEEKEND_MIN_SE_FACTOR * se) return emptyList()
        val agpWeekend = agpOf(weekend) ?: return emptyList()
        val agpWeekdays = agpOf(weekdays) ?: return emptyList()
        val better = diff > 0
        val from = input.startOfDayUtc(input.today.minusDays((WEEKEND_WINDOW_DAYS - 1).toLong()))
        return listOf(
            InsightCandidate(
                card = InsightCard(
                    id = "tod.weekend", category = InsightCategory.TIME_OF_DAY, detector = "weekend",
                    title = if (better) "Glucose is steadier on weekends" else "Time in range is lower on weekends",
                    summary = "Time in range was ${a.average().roundToInt()} % on weekends and ${b.average().roundToInt()} % on weekdays.",
                    miniGraph = MiniGraph.ProfileComparison("Weekends", agpWeekend.medianGraph(), "Weekdays", agpWeekdays.medianGraph()),
                    observationFromUtc = from, observationToUtc = input.nowUtc,
                    baselineFromUtc = from, baselineToUtc = input.nowUtc, baselineLabel = "Weekdays in the same period",
                    sampleSize = days.size, sampleUnit = "days", positiveCount = weekend.size,
                    effectSize = abs(diff), effectLabel = "${abs(diff).roundToInt()} points in range, ${weekend.size} weekend vs ${weekdays.size} weekday days",
                    uncertainty = "The difference is about ${(abs(diff) / se).let { "%.1f".format(it) }}× its likely margin of error (±${se.roundToInt()} points).",
                    limitations = listOf(
                        "Weekends have fewer days (${weekend.size}), so one unusual weekend can move the result.",
                        "Sleep times, meals and activity aren't separated here; they may all differ between weekends and weekdays."
                    ),
                    tone = if (better) InsightTone.FAVORABLE else InsightTone.UNFAVORABLE,
                    priority = rank(if (better) 30.0 else 35.0, (abs(diff) / 25.0).coerceIn(0.0, 1.0), days.size)
                ),
                topic = if (better) TipTopic.WEEKEND_BETTER else TipTopic.WEEKEND_WORSE
            )
        )
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Relevance weight x size of the effect x how much data backs it. */
    private fun rank(base: Double, effect: Double, validDays: Int): Double =
        base * (0.6 + 0.4 * effect) * (0.7 + 0.3 * (validDays / WINDOW_DAYS.toDouble()).coerceAtMost(1.0))

    private fun agpOf(days: List<DayGlucose>): PatternAgp? {
        val minutes = ArrayList<Int>(); val values = ArrayList<Int>()
        for (d in days) for (i in d.bins.indices) if (!d.bins[i].isNaN()) { minutes += i * BIN_MINUTES; values += d.bins[i].roundToInt() }
        return PatternAgp.build(minutes.toIntArray(), values.toIntArray(), days.size)
    }
}

/** 15-minute averages of the 5-minute AGP, whole mg/dL: small enough to store with the card. */
private fun DoubleArray.down(): List<Int> = toList().chunked(3).map { it.average().roundToInt() }

internal fun PatternAgp.toGraph(highlights: List<HourSpan>) =
    MiniGraph.AgpWindow(p10.down(), p25.down(), p50.down(), p75.down(), p90.down(), highlights)

internal fun PatternAgp.medianGraph(): List<Int> = p50.down()
