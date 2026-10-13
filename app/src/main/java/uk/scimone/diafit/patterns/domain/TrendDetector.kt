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
        c: Ctx, id: String, detector: String, title: Msg, summary: Msg, effect: Double, effectLabel: Msg, margin: Msg,
        better: Boolean, topic: TipTopic, escalate: Boolean = false, examples: List<PatternExample>? = null,
        metric: ((Stat) -> Double)? = null, describe: ((Stat) -> Msg)? = null
    ): PatternCandidate? {
        val a = agpOfDays(c.recent.map { it.day }) ?: return null
        val b = agpOfDays(c.before.map { it.day }) ?: return null
        val from = c.input.startOfDayUtc(c.before.first().day.date)
        // By default a typical recent day against a typical earlier day, on the metric that changed.
        val lastLabel = msg("pat_label_last_days", PERIOD_DAYS); val beforeLabel = msg("pat_label_days_before", PERIOD_DAYS)
        val cases = examples ?: if (metric != null && describe != null) {
            Cases.typical(c.recent, value = metric).map { Cases.day(c.input, it.day.date, describe(it), group = lastLabel) } +
                Cases.typical(c.before, value = metric).map { Cases.day(c.input, it.day.date, describe(it), group = beforeLabel) }
        } else emptyList()
        return PatternCandidate(
            PatternCard(
                id = id, category = PatternCategory.TREND, detector = detector, title = title, summary = summary,
                miniGraph = MiniGraph.ProfileComparison(lastLabel, a.medianGraph(), beforeLabel, b.medianGraph()),
                observationFromUtc = c.input.startOfDayUtc(c.recent.first().day.date), observationToUtc = c.input.nowUtc,
                baselineFromUtc = from, baselineToUtc = c.input.startOfDayUtc(c.recent.first().day.date), baselineLabel = beforeLabel,
                sampleSize = c.recent.size + c.before.size, sampleUnit = SampleUnit.DAYS, positiveCount = c.recent.size,
                effectSize = effect, effectLabel = effectLabel, uncertainty = margin,
                limitations = listOf(msg("pat_trend_lim1"), msg("pat_trend_lim2"), msg("pat_trend_lim3")),
                cases = cases,
                tone = if (better) PatternTone.FAVORABLE else PatternTone.UNFAVORABLE,
                priority = (if (better) 28.0 else 42.0) * (0.6 + 0.4 * effect.coerceIn(0.0, 1.0))
            ),
            topic, escalate
        )
    }

    private fun margin(diff: Double, se: Double, points: Boolean) =
        msg(if (points) "pat_tod_weekend_unc" else "pat_meal_margin", "%.1f".format(java.util.Locale.ROOT, abs(diff) / se.coerceAtLeast(0.01)), se.roundToInt())

    private fun tir(c: Ctx): PatternCandidate? {
        val r = c.recent.map { it.tir }; val b = c.before.map { it.tir }
        val diff = r.average() - b.average(); val se = se(r, b)
        if (abs(diff) < TIR_DIFF || abs(diff) < SE_FACTOR * se) return null
        val up = diff > 0
        return card(
            c, "trend.tir", "trend-tir", msg(if (up) "pat_trend_tir_up" else "pat_trend_tir_down"),
            msg(if (up) "pat_trend_tir_sum_up" else "pat_trend_tir_sum_down", PERIOD_DAYS, pct(r.average()), pct(b.average())),
            abs(diff) / 20.0, msg(if (up) "pat_trend_tir_eff_up" else "pat_sleep_effect", abs(diff).roundToInt()), margin(diff, se, true), up,
            if (up) TipTopic.TREND_BETTER else TipTopic.TREND_WORSE,
            metric = { it.tir }, describe = { msg("pat_trend_tir_case", pct(it.tir), it.mean.roundToInt()) }
        )
    }

    private fun mean(c: Ctx): PatternCandidate? {
        val r = c.recent.map { it.mean }; val b = c.before.map { it.mean }
        val diff = r.average() - b.average(); val se = se(r, b)
        val minDiff = c.input.scaled(MEAN_DIFF)
        if (abs(diff) < minDiff || abs(diff) < SE_FACTOR * se) return null
        val up = diff > 0
        return card(
            c, "trend.mean", "trend-mean", msg(if (up) "pat_trend_mean_up" else "pat_trend_mean_down"),
            msg(if (up) "pat_trend_mean_sum_up" else "pat_trend_mean_sum_down", PERIOD_DAYS, r.average().roundToInt(), b.average().roundToInt()),
            abs(diff) / 30.0, msg(if (up) "pat_trend_mean_eff_up" else "pat_trend_mean_eff_down", abs(diff).roundToInt()), margin(diff, se, false), !up,
            if (up) TipTopic.TREND_WORSE else TipTopic.TREND_BETTER,
            metric = { it.mean }, describe = { msg("pat_trend_mean_case", it.mean.roundToInt(), pct(it.tir)) }
        )
    }

    private fun variability(c: Ctx): PatternCandidate? {
        val r = c.recent.map { it.cv }; val b = c.before.map { it.cv }
        val diff = r.average() - b.average(); val se = se(r, b)
        if (abs(diff) < CV_DIFF || abs(diff) < SE_FACTOR * se) return null
        val up = diff > 0
        return card(
            c, "trend.variability", "trend-variability", msg(if (up) "pat_trend_var_up" else "pat_trend_var_down"),
            msg(if (up) "pat_trend_var_sum_up" else "pat_trend_var_sum_down", PERIOD_DAYS, pct(r.average()), pct(b.average())),
            abs(diff) / 10.0, msg(if (up) "pat_trend_var_eff_up" else "pat_trend_var_eff_down", abs(diff).roundToInt()), margin(diff, se, true), !up,
            if (up) TipTopic.TREND_WORSE else TipTopic.TREND_BETTER,
            metric = { it.cv }, describe = { msg("pat_trend_var_case", pct(it.cv), pct(it.tir)) }
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
            c, "trend.lows", "trend-lows", msg(if (up) "pat_trend_lows_up" else "pat_trend_lows_down"),
            msg("pat_trend_lows_summary", MIN_LOW_MIN, c.input.thresholds.low, rn, c.recent.size, bn, c.before.size, PERIOD_DAYS),
            abs(diff) / 0.4, msg("pat_trend_lows_effect", rn, bn),
            msg("pat_trend_lows_unc", c.recent.size, c.before.size), !up,
            if (up) TipTopic.TREND_LOWS_UP else TipTopic.TREND_BETTER, escalate = up && r >= 0.4,
            examples = (if (up) c.recent.filter { it.low } else c.before.filter { it.low }).takeLast(Cases.SINGLE).reversed().mapNotNull { st ->
                val (bin, v) = Cases.extreme(st.day, 0 until BINS_PER_DAY, lowest = true) ?: return@mapNotNull null
                val below = st.day.count(0 until BINS_PER_DAY) { it < c.input.thresholds.low } * BIN_MINUTES
                Cases.day(
                    c.input, st.day.date, msg("pat_trend_lows_case", v.roundToInt(), Cases.clock(bin), Cases.duration(below), c.input.thresholds.low), bin,
                    if (up) msg("pat_label_last_days", PERIOD_DAYS) else msg("pat_label_days_before", PERIOD_DAYS)
                )
            }
        )
    }
}
