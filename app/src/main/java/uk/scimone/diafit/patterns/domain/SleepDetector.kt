package uk.scimone.diafit.patterns.domain

import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Category D: how the night relates to the day after. Each night is the main sleep ending on a day (see [SleepNights]);
 * the outcome is the share of the rest of that day (from waking until midnight) spent in range, so the sleeping hours
 * themselves don't count. Findings compare nights that stood out for this person (shorter than their median, more
 * restless than their median, a bedtime far from their usual) with the others. Every finding is an association.
 */
object SleepDetector {
    const val WINDOW_DAYS = 28

    private const val MIN_NIGHTS = 10
    private const val MIN_GROUP = 5
    private const val MIN_COVERAGE = 0.7
    private const val MIN_DAY_BINS = 8 * BINS_PER_HOUR
    private const val TIR_DIFF = 8.0
    private const val SE_FACTOR = 2.0

    /** Short sleep: below the person's median and clearly (an hour) shorter than their other nights, and under this. */
    private const val SHORT_GAP_MIN = 60
    private const val SHORT_CEILING_MIN = 7 * 60
    /** Restless: above the median awake share, at least 1.5x it in the group and 5 points more than the rest. */
    private const val RESTLESS_RATIO = 1.5
    private const val RESTLESS_GAP = 0.05
    private const val IRREGULAR_MIN = 60

    private class Night(val n: SleepNight, val day: DayGlucose, val tir: Double)

    fun detect(input: DetectionInput): List<PatternCandidate> {
        val since = input.today.minusDays((WINDOW_DAYS - 1).toLong())
        val byDate = input.days.associateBy { it.date }
        val nights = input.nights.filter { it.wakeDate >= since && it.wakeDate < input.today }.mapNotNull { n ->
            val day = byDate[n.wakeDate] ?: return@mapNotNull null
            val wake = Instant.ofEpochMilli(n.endUtc).atZone(input.zone)
            val range = ((wake.hour * 60 + wake.minute) / BIN_MINUTES) until BINS_PER_DAY
            if (range.count() < MIN_DAY_BINS || day.coverage(range) < MIN_COVERAGE) return@mapNotNull null
            val v = range.map { day.bins[it] }.filter { !it.isNaN() }
            val t = input.thresholds
            Night(n, day, v.count { it >= t.low && it <= t.high }.toDouble() / v.size * 100)
        }
        if (nights.size < MIN_NIGHTS) return emptyList()
        val c = Ctx(input, nights, input.startOfDayUtc(since))
        return listOfNotNull(short(c), restless(c), irregular(c))
    }

    private class Ctx(val input: DetectionInput, val nights: List<Night>, val from: Long)

    private fun median(x: List<Double>) = x.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
    private fun variance(x: List<Double>) = if (x.size < 2) 0.0 else x.sumOf { (it - x.average()).let { d -> d * d } } / (x.size - 1)
    private fun dur(min: Int) = "${min / 60} h ${"%02d".format(min % 60)} min"

    /** Mean in-range share of [rest] minus [hit], with its standard error. */
    private fun gap(hit: List<Night>, rest: List<Night>): Pair<Double, Double> =
        (rest.map { it.tir }.average() - hit.map { it.tir }.average()) to
            sqrt(variance(hit.map { it.tir }) / hit.size + variance(rest.map { it.tir }) / rest.size)

    /** The hit nights did worse than the rest in both the earlier and the later half of the period. */
    private fun consistent(hit: List<Night>, rest: List<Night>, all: List<Night>): Boolean {
        val mid = all.sortedBy { it.n.wakeDate }[all.size / 2].n.wakeDate
        return listOf<(Night) -> Boolean>({ it.n.wakeDate < mid }, { it.n.wakeDate >= mid }).all { half ->
            val h = hit.filter(half); val r = rest.filter(half)
            h.size < 2 || r.size < 2 || r.map { it.tir }.average() - h.map { it.tir }.average() > 0
        }
    }

    private fun label(n: Night, zone: java.time.ZoneId, extra: String): String {
        val d = n.n.wakeDate
        return "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} · $extra · ${n.tir.roundToInt()} % in range"
    }

    private fun compare(
        c: Ctx, id: String, detector: String, title: String, summary: String, topic: TipTopic, hit: List<Night>, rest: List<Night>,
        hitLabel: String, restLabel: String, extra: (Night) -> String, limitation: String
    ): PatternCandidate? {
        if (hit.size < MIN_GROUP || rest.size < MIN_GROUP) return null
        val (diff, se) = gap(hit, rest)
        if (diff < TIR_DIFF || diff < SE_FACTOR * se || !consistent(hit, rest, c.nights)) return null
        val agpHit = agpOfDays(hit.map { it.day }) ?: return null
        val agpRest = agpOfDays(rest.map { it.day }) ?: return null
        val n = hit.size + rest.size
        return PatternCandidate(
            PatternCard(
                id = id, category = PatternCategory.SLEEP, detector = detector, title = title, summary = summary,
                miniGraph = MiniGraph.ProfileComparison(hitLabel, agpHit.medianGraph(), restLabel, agpRest.medianGraph()),
                observationFromUtc = c.from, observationToUtc = c.input.nowUtc, baselineFromUtc = c.from, baselineToUtc = c.input.nowUtc,
                baselineLabel = restLabel, sampleSize = n, sampleUnit = "nights", positiveCount = hit.size,
                effectSize = diff, effectLabel = "−${diff.roundToInt()} points in range",
                uncertainty = "The difference is about ${"%.1f".format(diff / se.coerceAtLeast(0.01))}× its likely margin of error (±${se.roundToInt()} points), and it points the same way in the earlier and the later half of the period.",
                limitations = listOf(
                    "Only the hours after waking count, and days with less than 70 % sensor data are left out.",
                    limitation,
                    "Meals, activity and stress differ between days too, so this can't show that sleep is the reason."
                ),
                examples = hit.sortedBy { it.tir }.take(3).map { label(it, c.input.zone, extra(it)) },
                tone = PatternTone.UNFAVORABLE,
                priority = 45.0 * (0.6 + 0.4 * (diff / 25.0).coerceIn(0.0, 1.0)) * (0.7 + 0.3 * (n / 20.0).coerceAtMost(1.0))
            ),
            topic
        )
    }

    private fun pct(x: Double) = "${x.roundToInt()} %"

    private fun short(c: Ctx): PatternCandidate? {
        val cut = median(c.nights.map { it.n.asleepMin.toDouble() })
        val (hit, rest) = c.nights.partition { it.n.asleepMin < cut }.let { (h, r) -> if (h.size >= MIN_GROUP) h to r else c.nights.partition { it.n.asleepMin <= cut } }
        if (hit.isEmpty() || rest.isEmpty()) return null
        val shortTypical = median(hit.map { it.n.asleepMin.toDouble() }); val restTypical = median(rest.map { it.n.asleepMin.toDouble() })
        if (shortTypical >= SHORT_CEILING_MIN || restTypical - shortTypical < SHORT_GAP_MIN) return null
        return compare(
            c, "sleep.short", "sleep-short", "Less time in range after short sleep",
            "After nights with under ${dur(cut.roundToInt())} of sleep (${hit.size}, typically ${dur(shortTypical.roundToInt())}) you spent ${pct(hit.map { it.tir }.average())} of the rest of the day in range, against ${pct(rest.map { it.tir }.average())} after longer nights (${rest.size}).",
            TipTopic.SLEEP_SHORT, hit, rest, "Short nights", "Longer nights", { dur(it.n.asleepMin) + " asleep" },
            "Short means under your median of ${dur(cut.roundToInt())}, and clearly shorter than your other nights."
        )
    }

    private fun restless(c: Ctx): PatternCandidate? {
        val withStages = c.nights.filter { it.n.awakeShare != null }
        if (withStages.size < MIN_NIGHTS) return null
        val cut = withStages.map { it.n.awakeShare!! }.sorted()[withStages.size / 2]
        val (hit, rest) = withStages.partition { it.n.awakeShare!! > cut }.let { (h, r) -> if (h.size >= MIN_GROUP) h to r else withStages.partition { it.n.awakeShare!! >= cut } }
        if (hit.isEmpty() || rest.isEmpty()) return null
        val hitShare = hit.map { it.n.awakeShare!! }.sorted()[hit.size / 2]; val restShare = rest.map { it.n.awakeShare!! }.sorted()[rest.size / 2]
        if (hitShare < RESTLESS_RATIO * restShare || hitShare - restShare < RESTLESS_GAP) return null
        return compare(
            c, "sleep.restless", "sleep-restless", "Less time in range after restless nights",
            "After the ${hit.size} most restless nights (typically ${pct(hitShare * 100)} of the night awake) you spent ${pct(hit.map { it.tir }.average())} of the rest of the day in range, against ${pct(rest.map { it.tir }.average())} after calmer nights (${rest.size}).",
            TipTopic.SLEEP_RESTLESS, hit, rest, "Restless nights", "Calmer nights", { pct(it.n.awakeShare!! * 100) + " awake" },
            "Restless means more time awake than on your median night; only nights where your watch or app records sleep stages count."
        )
    }

    private fun irregular(c: Ctx): PatternCandidate? {
        val usual = median(c.nights.map { it.n.bedtimeMin.toDouble() })
        val (hit, rest) = c.nights.partition { abs(it.n.bedtimeMin - usual) >= IRREGULAR_MIN }
        val clock = { m: Double -> (((m.roundToInt() + 18 * 60) % 1440).let { "%02d:%02d".format(it / 60, it % 60) }) }
        return compare(
            c, "sleep.irregular", "sleep-irregular", "Less time in range after irregular bedtimes",
            "After the ${hit.size} nights with a bedtime an hour or more away from your usual ${clock(usual)} you spent ${pct(hit.takeIf { it.isNotEmpty() }?.map { it.tir }?.average() ?: 0.0)} of the rest of the day in range, against ${pct(rest.takeIf { it.isNotEmpty() }?.map { it.tir }?.average() ?: 0.0)} after your usual nights (${rest.size}).",
            TipTopic.SLEEP_IRREGULAR, hit, rest, "Irregular bedtime", "Usual bedtime",
            { n -> Instant.ofEpochMilli(n.n.startUtc).atZone(c.input.zone).toLocalTime().let { "bed ${"%02d:%02d".format(it.hour, it.minute)}" } },
            "Your usual bedtime is the median of the period (${clock(usual)}); an hour or more earlier or later counts as irregular."
        )
    }
}
