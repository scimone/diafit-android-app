package uk.scimone.diafit.patterns.domain

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Category E: has the last two weeks been different from the two before? Complete days only (>= 70 % of the day
 * measured, today left out), 14 days against the 14 before, so a single odd day can't make a trend. Compared: time in
 * range, average glucose, variability (CV) and how often lows happened. Every finding needs enough days in both
 * periods and a difference clearly larger than the day-to-day noise (twice its standard error).
 */
object TrendDetector {
    const val PERIOD_DAYS = 14

    private const val MIN_DAYS = 8
    private const val MIN_COVERAGE = 0.7
    private const val TIR_DIFF = 7.0
    private const val MEAN_DIFF = 10.0
    private const val CV_DIFF = 4.0
    private const val LOW_DAYS_DIFF = 0.2
    private const val MIN_LOW_MIN = 15
    private const val SE_FACTOR = 2.0

    /** One complete day's numbers. */
    private class Stat(val day: DayGlucose, val tir: Double, val mean: Double, val cv: Double, val low: Boolean)

    private class Ctx(val input: DetectionInput, val recent: List<Stat>, val before: List<Stat>)

    fun detect(input: DetectionInput): List<PatternCandidate> {
        val t = input.thresholds
        val today = input.today
        fun stat(d: DayGlucose): Stat? {
            if (d.date >= today || d.coverage(0 until BINS_PER_DAY) < MIN_COVERAGE) return null
            val v = d.bins.filter { !it.isNaN() }
            val mean = v.average()
            val sd = sqrt(v.sumOf { (it - mean) * (it - mean) } / (v.size - 1).coerceAtLeast(1))
            val lowBins = v.count { it < t.low }
            return Stat(d, v.count { it >= t.low && it <= t.high }.toDouble() / v.size * 100, mean, sd / mean * 100, lowBins * BIN_MINUTES >= MIN_LOW_MIN)
        }
        val recentFrom = today.minusDays(PERIOD_DAYS.toLong()); val beforeFrom = today.minusDays(2L * PERIOD_DAYS)
        val stats = input.days.filter { it.date >= beforeFrom }.mapNotNull(::stat)
        val recent = stats.filter { it.day.date >= recentFrom }; val before = stats.filter { it.day.date < recentFrom }
        if (recent.size < MIN_DAYS || before.size < MIN_DAYS) return emptyList()
        val c = Ctx(input, recent, before)
        return listOfNotNull(tir(c), mean(c), variability(c), lows(c))
    }

    private fun variance(x: List<Double>) = if (x.size < 2) 0.0 else x.sumOf { (it - x.average()).let { d -> d * d } } / (x.size - 1)
    private fun se(a: List<Double>, b: List<Double>) = sqrt(variance(a) / a.size + variance(b) / b.size)
    private fun pct(x: Double) = "${x.roundToInt()} %"

    private fun card(
        c: Ctx, id: String, detector: String, title: String, summary: String, effect: Double, effectLabel: String, margin: String,
        better: Boolean, topic: TipTopic, escalate: Boolean = false, examples: List<String> = emptyList()
    ): PatternCandidate? {
        val a = agpOfDays(c.recent.map { it.day }) ?: return null
        val b = agpOfDays(c.before.map { it.day }) ?: return null
        val from = c.input.startOfDayUtc(c.before.first().day.date)
        return PatternCandidate(
            PatternCard(
                id = id, category = PatternCategory.TREND, detector = detector, title = title, summary = summary,
                miniGraph = MiniGraph.ProfileComparison("Last $PERIOD_DAYS days", a.medianGraph(), "The $PERIOD_DAYS days before", b.medianGraph()),
                observationFromUtc = c.input.startOfDayUtc(c.recent.first().day.date), observationToUtc = c.input.nowUtc,
                baselineFromUtc = from, baselineToUtc = c.input.startOfDayUtc(c.recent.first().day.date), baselineLabel = "The $PERIOD_DAYS days before",
                sampleSize = c.recent.size + c.before.size, sampleUnit = "days", positiveCount = c.recent.size,
                effectSize = effect, effectLabel = effectLabel, uncertainty = margin,
                limitations = listOf(
                    "Only days with at least 70 % sensor data count, and today is left out.",
                    "Two weeks is a short stretch: illness, travel, a new sensor or a change of routine can move these numbers.",
                    "This compares two periods and can't show what caused the difference."
                ),
                examples = examples,
                tone = if (better) PatternTone.FAVORABLE else PatternTone.UNFAVORABLE,
                priority = (if (better) 28.0 else 42.0) * (0.6 + 0.4 * effect.coerceIn(0.0, 1.0))
            ),
            topic, escalate
        )
    }

    private fun margin(diff: Double, se: Double, unit: String) =
        "The difference is about ${"%.1f".format(abs(diff) / se.coerceAtLeast(0.01))}× its likely margin of error (±${se.roundToInt()} $unit)."

    private fun tir(c: Ctx): PatternCandidate? {
        val r = c.recent.map { it.tir }; val b = c.before.map { it.tir }
        val diff = r.average() - b.average(); val se = se(r, b)
        if (abs(diff) < TIR_DIFF || abs(diff) < SE_FACTOR * se) return null
        val up = diff > 0
        return card(
            c, "trend.tir", "trend-tir", if (up) "More time in range lately" else "Less time in range lately",
            "Over the last $PERIOD_DAYS days you spent ${pct(r.average())} of the time in range, ${if (up) "up" else "down"} from ${pct(b.average())} in the $PERIOD_DAYS days before.",
            abs(diff) / 20.0, "${if (up) "+" else "−"}${abs(diff).roundToInt()} points in range", margin(diff, se, "points"), up,
            if (up) TipTopic.TREND_BETTER else TipTopic.TREND_WORSE
        )
    }

    private fun mean(c: Ctx): PatternCandidate? {
        val r = c.recent.map { it.mean }; val b = c.before.map { it.mean }
        val diff = r.average() - b.average(); val se = se(r, b)
        val minDiff = c.input.scaled(MEAN_DIFF)
        if (abs(diff) < minDiff || abs(diff) < SE_FACTOR * se) return null
        val up = diff > 0
        return card(
            c, "trend.mean", "trend-mean", if (up) "Higher average glucose lately" else "Lower average glucose lately",
            "Average glucose over the last $PERIOD_DAYS days was ${r.average().roundToInt()} mg/dL, ${if (up) "up" else "down"} from ${b.average().roundToInt()} in the $PERIOD_DAYS days before.",
            abs(diff) / 30.0, "${if (up) "+" else "−"}${abs(diff).roundToInt()} mg/dL average", margin(diff, se, "mg/dL"), !up,
            if (up) TipTopic.TREND_WORSE else TipTopic.TREND_BETTER
        )
    }

    private fun variability(c: Ctx): PatternCandidate? {
        val r = c.recent.map { it.cv }; val b = c.before.map { it.cv }
        val diff = r.average() - b.average(); val se = se(r, b)
        if (abs(diff) < CV_DIFF || abs(diff) < SE_FACTOR * se) return null
        val up = diff > 0
        return card(
            c, "trend.variability", "trend-variability", if (up) "Glucose more variable lately" else "Glucose steadier lately",
            "Day-to-day swings (coefficient of variation) averaged ${pct(r.average())} over the last $PERIOD_DAYS days, ${if (up) "up" else "down"} from ${pct(b.average())} in the $PERIOD_DAYS days before.",
            abs(diff) / 10.0, "${if (up) "+" else "−"}${abs(diff).roundToInt()} points variability", margin(diff, se, "points"), !up,
            if (up) TipTopic.TREND_WORSE else TipTopic.TREND_BETTER
        )
    }

    private fun lows(c: Ctx): PatternCandidate? {
        val r = c.recent.count { it.low }.toDouble() / c.recent.size; val b = c.before.count { it.low }.toDouble() / c.before.size
        val diff = r - b
        if (abs(diff) < LOW_DAYS_DIFF) return null
        val up = diff > 0
        val rn = c.recent.count { it.low }; val bn = c.before.count { it.low }
        if (up && rn < 3) return null
        return card(
            c, "trend.lows", "trend-lows", if (up) "Lows more often lately" else "Fewer lows lately",
            "A low (at least $MIN_LOW_MIN minutes under ${c.input.thresholds.low} mg/dL) happened on $rn of the last ${c.recent.size} days, against $bn of ${c.before.size} in the $PERIOD_DAYS days before.",
            abs(diff) / 0.4, "$rn vs $bn days with a low",
            "Counts of days, not a measured margin: ${c.recent.size} and ${c.before.size} days were compared.", !up,
            if (up) TipTopic.TREND_LOWS_UP else TipTopic.TREND_BETTER, escalate = up && r >= 0.4,
            examples = c.recent.filter { it.low }.takeLast(3).map { it.day.date.toString() }
        )
    }
}
