package uk.scimone.diafit.patterns.domain

import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Category F: findings that need two kinds of data together, which no single-family detector can see:
 *  - a late dinner and the glucose through the following night (meals + time of day),
 *  - activity in the evening and lows overnight (activity + time of day),
 *  - a short night and the rise after the next breakfast (sleep + meals).
 * Same rules as the others: complete-enough data, groups that stand out for this person, an effect clearly larger than
 * the noise (twice its standard error), the same direction in both halves of the period. Associations only.
 */
object CombinedDetector {
    const val WINDOW_DAYS = LONG_WINDOW_DAYS

    private const val MIN_GROUP = 5
    private const val MIN_COVERAGE = 0.7
    private const val SE_FACTOR = 2.0
    private const val MIN_DINNER_CARBS = 20
    private const val DINNER_FROM_HOUR = 17
    private const val LATE_GAP_MIN = 60
    private const val NIGHT_DIFF = 20.0
    private const val NIGHT_END_BIN = 6 * BINS_PER_HOUR

    private const val EVENING_FROM_HOUR = 17
    private const val EVENING_TO_HOUR = 22
    private const val MIN_ACTIVITY_MIN = 20
    private const val LOW_NIGHT_BINS = 3
    private const val LOW_SHARE_DIFF = 0.25
    private const val MIN_LOW_NIGHTS = 3

    private const val BREAKFAST_FROM_HOUR = 5
    private const val BREAKFAST_TO_HOUR = 11
    private const val MIN_BREAKFAST_CARBS = 15
    private const val RISE_DIFF = 25.0
    private const val SHORT_GAP_MIN = 60
    private const val SHORT_CEILING_MIN = 7 * 60

    private const val FIRST_K = -6
    private const val LAST_K = 120 // 10 h
    private const val BREAKFAST_LAST_K = 48 // 4 h

    fun detect(input: DetectionInput): List<PatternCandidate> {
        val c = Ctx(input, WINDOW_DAYS)
        return listOfNotNull(lateDinner(c), eveningLow(c), shortSleepBreakfast(c))
    }

    private class Ctx(val input: DetectionInput, windowDays: Int) {
        val first: LocalDate = input.today.minusDays((windowDays - 1).toLong())
        val from = input.startOfDayUtc(first)
        val byDate = input.days.associateBy { it.date }
        val lookup = GlucoseLookup(input.days, input.zone)
    }

    private fun median(x: List<Double>) = x.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
    private fun variance(x: List<Double>) = if (x.size < 2) 0.0 else x.sumOf { (it - x.average()).let { d -> d * d } } / (x.size - 1)
    private fun se(a: List<Double>, b: List<Double>) = sqrt(variance(a) / a.size + variance(b) / b.size)
    private fun pct(x: Double) = "${(x * 100).roundToInt()} %"
    private fun hhmm(min: Double) = "%02d:%02d".format(min.roundToInt() / 60, min.roundToInt() % 60)
    private fun margin(diff: Double, se: Double, unit: String) =
        "The difference is about ${"%.1f".format(kotlin.math.abs(diff) / se.coerceAtLeast(0.01))}× its likely margin of error (±${se.roundToInt()} $unit), and it points the same way in the earlier and the later half of the period."

    /** [worse] is better than [other] in both halves of the period (by date). */
    private fun <T> consistent(all: List<T>, date: (T) -> LocalDate, hit: List<T>, rest: List<T>, value: (T) -> Double, higherIsWorse: Boolean = true): Boolean {
        val mid = all.map(date).sorted()[all.size / 2]
        return listOf<(T) -> Boolean>({ date(it) < mid }, { date(it) >= mid }).all { half ->
            val h = hit.filter(half); val r = rest.filter(half)
            h.size < 2 || r.size < 2 || (h.map(value).average() - r.map(value).average()).let { if (higherIsWorse) it > 0 else it < 0 }
        }
    }

    private fun card(
        c: Ctx, id: String, detector: String, title: String, summary: String, graph: MiniGraph, n: Int, positive: Int, effect: Double,
        effectLabel: String, uncertainty: String, limits: List<String>, examples: List<PatternExample>, baseline: String, priority: Double
    ) = PatternCard(
        id = id, category = PatternCategory.COMBINED, detector = detector, title = title, summary = summary, miniGraph = graph,
        observationFromUtc = c.from, observationToUtc = c.input.nowUtc, baselineFromUtc = c.from, baselineToUtc = c.input.nowUtc, baselineLabel = baseline,
        sampleSize = n, sampleUnit = "days", positiveCount = positive, effectSize = effect, effectLabel = effectLabel,
        uncertainty = uncertainty, limitations = limits, cases = examples, tone = PatternTone.UNFAVORABLE, priority = priority
    )

    private fun rank(base: Double, effect: Double, n: Int) = base * (0.6 + 0.4 * effect.coerceIn(0.0, 1.0)) * (0.7 + 0.3 * (n / 15.0).coerceAtMost(1.0))

    // ---- late dinner -> glucose overnight -------------------------------------------------------------------------

    /** One evening's last big meal and the glucose that night; [fatProtein] is null when neither was recorded. */
    internal class Dinner(val date: LocalDate, val minute: Double, val startUtc: Long, val night: Double, val values: FloatArray, val baseline: Float, val fatProtein: Int?, val carbs: Int) {
        /** The dinner and the night after it (to 07:00), with midnight to 06:00 shaded. */
        fun case(input: DetectionInput, note: String, group: String): PatternExample {
            val midnight = input.startOfDayUtc(date.plusDays(1))
            return PatternExample(
                fromUtc = startUtc - 3_600_000L, toUtc = midnight + 7 * 3_600_000L, focusFromUtc = midnight, focusToUtc = midnight + 6 * 3_600_000L,
                note = "$note · average ${night.roundToInt()} mg/dL 00:00–06:00", eventUtc = startUtc,
                eventLabel = "Dinner · $carbs g" + (fatProtein?.let { " · $it g fat + protein" } ?: ""), group = group
            )
        }
    }

    internal fun dinnersOf(input: DetectionInput, windowDays: Int): List<Dinner> = dinners(Ctx(input, windowDays))

    private fun dinners(c: Ctx): List<Dinner> {
        val zone = c.input.zone
        val step = BIN_MINUTES * 60_000L
        return c.input.sittings.groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }.mapNotNull { (date, list) ->
            if (date < c.first || date >= c.input.today.minusDays(1)) return@mapNotNull null
            val s = list.filter { it.totalCarbs >= MIN_DINNER_CARBS && Instant.ofEpochMilli(it.startTime).atZone(zone).hour >= DINNER_FROM_HOUR }
                .maxByOrNull { it.startTime } ?: return@mapNotNull null
            val next = c.byDate[date.plusDays(1)]?.takeIf { it.coverage(0 until NIGHT_END_BIN) >= MIN_COVERAGE } ?: return@mapNotNull null
            val local = Instant.ofEpochMilli(s.startTime).atZone(zone)
            val values = FloatArray(LAST_K - FIRST_K + 1) { c.lookup.at(s.startTime + (it + FIRST_K) * step) }
            val before = (-2..0).map { values[it - FIRST_K] }.filter { !it.isNaN() }
            if (before.isEmpty()) return@mapNotNull null
            Dinner(
                date, (local.hour * 60 + local.minute).toDouble(), s.startTime, next.mean(0 until NIGHT_END_BIN).toDouble(), values, before.average().toFloat(),
                s.courses.sumOf { (it.proteins ?: 0) + (it.fats ?: 0) }.takeIf { it > 0 }, s.totalCarbs
            )
        }
    }

    private fun lateDinner(c: Ctx): PatternCandidate? {
        val dinners = dinners(c)
        if (dinners.size < 2 * MIN_GROUP) return null
        val cut = median(dinners.map { it.minute })
        val (late, early) = dinners.partition { it.minute > cut }.let { (l, e) -> if (l.size >= MIN_GROUP) l to e else dinners.partition { it.minute >= cut } }
        if (late.size < MIN_GROUP || early.size < MIN_GROUP) return null
        val lateTypical = median(late.map { it.minute }); val earlyTypical = median(early.map { it.minute })
        if (lateTypical - earlyTypical < LATE_GAP_MIN) return null
        val diff = late.map { it.night }.average() - early.map { it.night }.average()
        val se = se(late.map { it.night }, early.map { it.night })
        val minDiff = c.input.scaled(NIGHT_DIFF)
        if (diff < minDiff || diff < SE_FACTOR * se || !consistent(dinners, { it.date }, late, early, { it.night })) return null
        fun curves(l: List<Dinner>) = l.map { EventCurve(it.values, it.baseline) }
        val graph = eventGraph(curves(late), curves(early), FIRST_K, LAST_K, 6, "Late dinners", "Earlier dinners", "dinner", null, listOf(0, 120, 240, 360, 480, 600))
        return PatternCandidate(
            card(
                c, "combo.latedinner", "combo-latedinner", "Higher glucose overnight after late dinners",
                "After dinners starting around ${hhmm(lateTypical)} or later (${late.size}) glucose between midnight and 06:00 averaged ${diff.roundToInt()} mg/dL more than after earlier dinners, typically ${hhmm(earlyTypical)} (${early.size}).",
                graph, dinners.size, late.size, diff, "+${diff.roundToInt()} mg/dL overnight", margin(diff, se, "mg/dL"),
                listOf(
                    "Only dinners of at least $MIN_DINNER_CARBS g carbs starting after $DINNER_FROM_HOUR:00 count, on nights with at least 70 % sensor data.",
                    "Late means later than your median dinner time (${hhmm(cut)}) and clearly later than your earlier dinners.",
                    "Dinner size, insulin timing and activity differ between days too, so this can't show that the time of eating is the reason."
                ),
                Cases.typical(late) { it.night }.map { it.case(c.input, "Dinner at ${hhmm(it.minute)}", "Late dinners") } +
                    Cases.typical(early) { it.night }.map { it.case(c.input, "Dinner at ${hhmm(it.minute)}", "Earlier dinners") },
                "Earlier dinners", rank(50.0, diff / 50.0, dinners.size)
            ),
            TipTopic.COMBO_LATE_DINNER
        )
    }

    // ---- evening activity -> overnight lows ---------------------------------------------------------------------

    private class EveningNight(val date: LocalDate, val active: Boolean, val low: Boolean, val next: DayGlucose)

    private fun eveningLow(c: Ctx): PatternCandidate? {
        val zone = c.input.zone
        val eveningEnds = c.input.events.filter { it.durationMin >= MIN_ACTIVITY_MIN }.map { Instant.ofEpochMilli(it.endUtc).atZone(zone) }
            .filter { it.hour in EVENING_FROM_HOUR until EVENING_TO_HOUR }.map { it.toLocalDate() }.toSet()
        val low = c.input.thresholds.low
        val nights = generateSequence(c.first) { it.plusDays(1) }.takeWhile { it < c.input.today.minusDays(1) }.mapNotNull { d ->
            val today = c.byDate[d] ?: return@mapNotNull null
            val next = c.byDate[d.plusDays(1)] ?: return@mapNotNull null
            // 22:00 to 06:00: the end of this day and the start of the next.
            val bins = (22 * BINS_PER_HOUR until BINS_PER_DAY).map { today.bins[it] } + (0 until NIGHT_END_BIN).map { next.bins[it] }
            val v = bins.filter { !it.isNaN() }
            if (v.size.toDouble() / bins.size < MIN_COVERAGE) return@mapNotNull null
            EveningNight(d, d in eveningEnds, v.count { it < low } >= LOW_NIGHT_BINS, next)
        }.toList()
        val active = nights.filter { it.active }; val rest = nights.filter { !it.active }
        if (active.size < MIN_GROUP || rest.size < MIN_GROUP) return null
        val lows = active.count { it.low }
        val a = lows.toDouble() / active.size; val b = rest.count { it.low }.toDouble() / rest.size
        if (lows < MIN_LOW_NIGHTS || a - b < LOW_SHARE_DIFF) return null
        val v = { n: EveningNight -> if (n.low) 1.0 else 0.0 }
        if (!consistent(nights, { it.date }, active, rest, v)) return null
        val agpA = agpOfDays(active.map { it.next }) ?: return null
        val agpB = agpOfDays(rest.map { it.next }) ?: return null
        val events = c.input.events.filter { it.durationMin >= MIN_ACTIVITY_MIN && Instant.ofEpochMilli(it.endUtc).atZone(zone).hour in EVENING_FROM_HOUR until EVENING_TO_HOUR }
        // An evening and its night (16:00 to 07:00), 22:00 to 06:00 shaded, the evening activity anchored, the lowest point marked.
        fun ex(n: EveningNight, group: String): PatternExample {
            val day0 = c.input.startOfDayUtc(n.date)
            val h = 3_600_000L
            val lowest = (c.lookup.let { l -> generateSequence(day0 + 22 * h) { it + BIN_MINUTES * 60_000L }.takeWhile { it < day0 + 30 * h }.map { it to l.at(it) } })
                .filter { !it.second.isNaN() }.minByOrNull { it.second }
            val e = events.lastOrNull { Instant.ofEpochMilli(it.endUtc).atZone(zone).toLocalDate() == n.date }
            return PatternExample(
                fromUtc = day0 + 16 * h, toUtc = day0 + 31 * h, focusFromUtc = day0 + 22 * h, focusToUtc = day0 + 30 * h,
                note = lowest?.let { "Lowest ${it.second.roundToInt()} mg/dL overnight" + if (n.low) "" else ", no low" } ?: "Overnight",
                eventUtc = e?.startUtc, eventEndUtc = e?.endUtc, eventLabel = e?.let { "${it.label} · ${Cases.duration(it.durationMin)}" },
                markUtc = lowest?.first, group = group
            )
        }
        val examples = active.filter { it.low }.takeLast(Cases.PER_GROUP).reversed().map { ex(it, "Night after evening activity") } +
            rest.filter { !it.low }.takeLast(Cases.PER_GROUP).reversed().map { ex(it, "Other nights") }
        return PatternCandidate(
            card(
                c, "combo.eveninglow", "combo-eveninglow", "Overnight lows after evening activity",
                "On $lows of ${active.size} days with activity ending between $EVENING_FROM_HOUR:00 and $EVENING_TO_HOUR:00, glucose went below $low mg/dL for at least ${LOW_NIGHT_BINS * BIN_MINUTES} minutes between 22:00 and 06:00, against ${rest.count { it.low }} of ${rest.size} days without evening activity.",
                MiniGraph.ProfileComparison("Night after evening activity", agpA.medianGraph(), "Other nights", agpB.medianGraph()),
                nights.size, lows, a - b, "${pct(a)} vs ${pct(b)} of nights", "Counts of nights, not a measured margin: ${active.size} and ${rest.size} nights were compared, and the difference points the same way in both halves of the period.",
                listOf(
                    "Activity of at least $MIN_ACTIVITY_MIN minutes counts, from logged workouts and stretches of raised heart rate or steps.",
                    "Evening meals and insulin differ between days too, so this can't show that activity is the reason.",
                    "Nights with less than 70 % sensor data are left out."
                ),
                examples, "Nights without evening activity",
                rank(80.0, (a - b) / 0.5, nights.size)
            ),
            TipTopic.COMBO_EVENING_LOW, escalate = a >= 0.4
        )
    }

    // ---- short night -> breakfast rise --------------------------------------------------------------------------

    private class Breakfast(val night: SleepNight, val rise: Double, val values: FloatArray, val baseline: Float, val startUtc: Long, val carbs: Int) {
        /** The night and the breakfast after it (to 4 h after eating), the first 3 h after eating shaded and the peak marked. */
        fun case(group: String): PatternExample {
            val peakK = (0..36).filter { !values[it - FIRST_K].isNaN() }.maxByOrNull { values[it - FIRST_K] }
            return PatternExample(
                fromUtc = minOf(night.startUtc, startUtc - 3_600_000L), toUtc = startUtc + 4 * 3_600_000L,
                focusFromUtc = startUtc, focusToUtc = startUtc + 3 * 3_600_000L,
                note = "Asleep ${Cases.duration(night.asleepMin)} · breakfast rise ${Cases.signed(rise)} mg/dL",
                eventUtc = startUtc, eventLabel = "Breakfast · $carbs g", markUtc = peakK?.let { startUtc + it * BIN_MINUTES * 60_000L }, group = group
            )
        }
    }

    private fun shortSleepBreakfast(c: Ctx): PatternCandidate? {
        val zone = c.input.zone
        val step = BIN_MINUTES * 60_000L
        val byDate = c.input.sittings.groupBy { Instant.ofEpochMilli(it.startTime).atZone(zone).toLocalDate() }
        val items = c.input.nights.filter { it.wakeDate >= c.first && it.wakeDate < c.input.today }.mapNotNull { n ->
            val s = byDate[n.wakeDate]?.filter {
                it.totalCarbs >= MIN_BREAKFAST_CARBS && Instant.ofEpochMilli(it.startTime).atZone(zone).hour in BREAKFAST_FROM_HOUR until BREAKFAST_TO_HOUR &&
                    it.startTime >= n.endUtc - 30 * 60_000L
            }?.minByOrNull { it.startTime } ?: return@mapNotNull null
            if (s.startTime + BREAKFAST_LAST_K * step > c.input.nowUtc) return@mapNotNull null
            val values = FloatArray(BREAKFAST_LAST_K - FIRST_K + 1) { c.lookup.at(s.startTime + (it + FIRST_K) * step) }
            val before = (-2..0).map { values[it - FIRST_K] }.filter { !it.isNaN() }
            val after = (0..36).map { values[it - FIRST_K] }
            if (before.isEmpty() || after.count { !it.isNaN() }.toDouble() / after.size < MIN_COVERAGE) return@mapNotNull null
            val baseline = before.average().toFloat()
            Breakfast(n, after.filter { !it.isNaN() }.max() - baseline.toDouble(), values, baseline, s.startTime, s.totalCarbs)
        }
        if (items.size < 2 * MIN_GROUP) return null
        val cut = median(items.map { it.night.asleepMin.toDouble() })
        val (short, rest) = items.partition { it.night.asleepMin < cut }.let { (s, r) -> if (s.size >= MIN_GROUP) s to r else items.partition { it.night.asleepMin <= cut } }
        if (short.size < MIN_GROUP || rest.size < MIN_GROUP) return null
        val shortTypical = median(short.map { it.night.asleepMin.toDouble() }); val restTypical = median(rest.map { it.night.asleepMin.toDouble() })
        if (shortTypical >= SHORT_CEILING_MIN || restTypical - shortTypical < SHORT_GAP_MIN) return null
        val diff = short.map { it.rise }.average() - rest.map { it.rise }.average()
        val se = se(short.map { it.rise }, rest.map { it.rise })
        if (diff < c.input.scaled(RISE_DIFF) || diff < SE_FACTOR * se || !consistent(items, { it.night.wakeDate }, short, rest, { it.rise })) return null
        fun curves(l: List<Breakfast>) = l.map { EventCurve(it.values, it.baseline) }
        val graph = eventGraph(curves(short), curves(rest), FIRST_K, BREAKFAST_LAST_K, 3, "After short nights", "After longer nights", "breakfast", null, listOf(0, 60, 120, 180, 240))
        fun dur(m: Double) = "${m.toInt() / 60} h ${"%02d".format(m.toInt() % 60)} min"
        return PatternCandidate(
            card(
                c, "combo.shortbreakfast", "combo-shortbreakfast", "Bigger breakfast rise after short sleep",
                "After nights of under ${dur(cut)} of sleep (${short.size}) glucose rose ${short.map { it.rise }.average().roundToInt()} mg/dL after breakfast, against ${rest.map { it.rise }.average().roundToInt()} mg/dL after longer nights (${rest.size}).",
                graph, items.size, short.size, diff, "+${diff.roundToInt()} mg/dL breakfast rise", margin(diff, se, "mg/dL"),
                listOf(
                    "Only breakfasts of at least $MIN_BREAKFAST_CARBS g carbs eaten between $BREAKFAST_FROM_HOUR:00 and $BREAKFAST_TO_HOUR:00 after waking count.",
                    "Short means under your median of ${dur(cut)}, and clearly shorter than your other nights.",
                    "What you ate and the insulin you had differ between days too, so this can't show that sleep is the reason."
                ),
                Cases.typical(short) { it.rise }.map { it.case("After short nights") } + Cases.typical(rest) { it.rise }.map { it.case("After longer nights") },
                "After longer nights", rank(45.0, diff / 60.0, items.size)
            ),
            TipTopic.COMBO_SLEEP_BREAKFAST
        )
    }
}
