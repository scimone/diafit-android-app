package uk.scimone.diafit.insights.domain

import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Category C: recurring associations between activity and glucose. An event is a logged workout or a stretch of raised
 * activity found in heart rate and steps (see [ActivityEvents]); glucose is read from 30 min before its start to 8 h
 * after its end, as the change from the level just before. Immediate (during and the 2 h after) and delayed (2-8 h
 * after) effects are kept apart. Events are left out when the response can't be read cleanly: a meal close by, another
 * activity within 2 h, too little sensor data, or glucose already low at the start. Insulin activity can't be separated
 * out; every finding is an association.
 */
object ExerciseDetector {
    const val WINDOW_DAYS = 28

    private const val MIN_EVENT_MIN = 20
    private const val MIN_COVERAGE = 0.7
    private const val MIN_EVENTS = 6
    private const val MIN_GROUP = 5
    private const val MIN_LOW_EVENTS = 3
    private const val MIN_LOW_SHARE = 0.20

    private const val FIRST_K = -6
    private const val LAST_K = 96 // start + 8 h
    private const val GRAPH_STEP = 3

    private const val DROP = 30.0
    private const val LONG_MIN = 45
    private const val EVENING_HOUR = 17
    private const val COMPARE_DIFF = 20.0
    private const val INTENSITY_DIFF = 25.0
    private const val SE_FACTOR = 2.0
    private const val ACTIVE_DAY_MIN = 30
    private const val NEXT_DAY_TIR_DIFF = 8.0
    private const val MIN_DAYS_PER_GROUP = 6

    /** One event's measured glucose response; null fields = not measurable for this event. */
    class Response(
        val event: ActivityEvent, val baseline: Float, val values: FloatArray, val change: Float,
        val immediateLow: Boolean, val delayedLow: Boolean?, val lowestAfter: Float?, val lowestAt: Int?
    ) {
        val minChange: Float? get() = lowestAfter?.let { it - baseline }
    }

    fun detect(input: DetectionInput): List<InsightCandidate> {
        val responses = responses(input)
        val out = ArrayList<InsightCandidate>()
        val c = Ctx(input, responses)
        if (responses.size >= MIN_EVENTS) {
            out += scoped(c, ::drops)
            out += scoped(c, ::immediateLows)
            out += scoped(c, ::delayedLows)
            out += scoped(c, ::rises)
            out += scoped(c, ::steady).sortedByDescending { it.card.priority }.take(1)
            out += longVsShort(c)
            out += evening(c)
            out += intensity(c)
        }
        out += nextDay(input)
        return out
    }

    private class Ctx(val input: DetectionInput, val all: List<Response>) {
        val t = input.thresholds
        val from = input.startOfDayUtc(input.today.minusDays((WINDOW_DAYS - 1).toLong()))
        val to = input.nowUtc
    }

    // -------------------------------------------------------------------------------------------------------------

    private fun responses(input: DetectionInput): List<Response> {
        val lookup = GlucoseLookup(input.days, input.zone)
        val low = input.thresholds.low
        val since = input.startOfDayUtc(input.today.minusDays((WINDOW_DAYS - 1).toLong()))
        val events = input.events.sortedBy { it.startUtc }
        val min = 60_000L
        val step = BIN_MINUTES * min
        val out = ArrayList<Response>()
        for ((i, e) in events.withIndex()) {
            if (e.startUtc < since || e.durationMin < MIN_EVENT_MIN || e.endUtc + 120 * min > input.nowUtc) continue
            if (events.any { it !== e && ((it.startUtc > e.startUtc && it.startUtc <= e.endUtc + 120 * min) || (it.endUtc >= e.startUtc - 120 * min && it.endUtc < e.startUtc)) }) continue
            if (input.sittings.any { it.totalCarbs >= 15 && it.startTime in (e.startUtc - 90 * min)..(e.endUtc + 120 * min) }) continue

            val values = FloatArray(LAST_K - FIRST_K + 1) { lookup.at(e.startUtc + (it + FIRST_K) * step) }
            val before = (-4..0).map { values[it - FIRST_K] }.filter { !it.isNaN() }
            if (before.isEmpty()) continue
            val baseline = before.average().toFloat()
            if (baseline < low) continue

            fun bins(fromMs: Long, toMs: Long) = generateSequence(fromMs) { it + step }.takeWhile { it <= toMs }.map { lookup.at(it) }.toList()
            val upToTwoHours = bins(e.startUtc - 30 * min, e.endUtc + 120 * min)
            if (upToTwoHours.count { !it.isNaN() }.toDouble() / upToTwoHours.size < MIN_COVERAGE) continue
            val endBins = bins(e.endUtc - 10 * min, e.endUtc).filter { !it.isNaN() }
            if (endBins.isEmpty()) continue
            val change = endBins.average().toFloat() - baseline
            val immediate = bins(e.endUtc + step, e.endUtc + 120 * min).filter { !it.isNaN() }.count { it < low } >= 3

            var delayed: Boolean? = null
            var lowest: Float? = null
            var lowestAt: Int? = null
            if (e.endUtc + 8 * 60 * min <= input.nowUtc) {
                val late = bins(e.endUtc + 120 * min + step, e.endUtc + 8 * 60 * min)
                if (late.count { !it.isNaN() }.toDouble() / late.size >= MIN_COVERAGE) {
                    delayed = late.filter { !it.isNaN() }.count { it < low } >= 3
                    val after = bins(e.endUtc, e.endUtc + 8 * 60 * min).withIndex().filter { !it.value.isNaN() }.minBy { it.value }
                    lowest = after.value; lowestAt = after.index * BIN_MINUTES
                }
            }
            out += Response(e, baseline, values, change, immediate, delayed, lowest, lowestAt)
        }
        return out
    }

    // -------------------------------------------------------------------------------------------------------------

    private fun scopeLabel(label: String?) = label?.lowercase() ?: "activity"

    /** The whole set first; only when that finds nothing, each kind of workout with enough events on its own. */
    private fun scoped(c: Ctx, eval: (Ctx, List<Response>, String?) -> InsightCandidate?): List<InsightCandidate> {
        eval(c, c.all, null)?.let { return listOf(it) }
        return c.all.groupBy { it.event.label }.mapNotNull { (label, g) -> if (g.size < MIN_EVENTS) null else eval(c, g, label) }
    }

    private fun halves(g: List<Response>) = g.sortedBy { it.event.startUtc }.let { it.take(it.size / 2) to it.drop(it.size / 2) }
    private fun median(x: List<Double>) = x.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
    private fun share(n: Int, d: Int) = if (d == 0) 0.0 else n.toDouble() / d
    private fun pct(x: Double) = "${(x * 100).roundToInt()} %"
    private fun variance(x: List<Double>) = if (x.size < 2) 0.0 else x.sumOf { (it - x.average()).let { d -> d * d } } / (x.size - 1)
    private fun welch(a: List<Double>, b: List<Double>) = (a.average() - b.average()) to sqrt(variance(a) / a.size + variance(b) / b.size)
    private fun margin(diff: Double, se: Double) = "The difference is about ${"%.1f".format(abs(diff) / se.coerceAtLeast(0.01))}× its likely margin of error (±${se.roundToInt()} mg/dL)."

    private fun whenLabel(r: Response, zone: java.time.ZoneId): String {
        val d = Instant.ofEpochMilli(r.event.startUtc).atZone(zone)
        return "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${r.event.label.lowercase()} (${r.event.durationMin} min)"
    }

    private fun rank(base: Double, effect: Double, n: Int) = base * (0.6 + 0.4 * effect.coerceIn(0.0, 1.0)) * (0.7 + 0.3 * (n / 15.0).coerceAtMost(1.0))

    private fun graph(g: List<Response>, compare: List<Response>?, aLabel: String, bLabel: String?): MiniGraph =
        eventGraph(
            g.map { EventCurve(it.values, it.baseline) }, compare?.map { EventCurve(it.values, it.baseline) },
            FIRST_K, LAST_K, GRAPH_STEP, aLabel, bLabel, "start", median(g.map { it.event.durationMin.toDouble() }).roundToInt(),
            listOf(0, 120, 240, 360, 480)
        )

    private fun limitations(extra: String) = listOf(
        "Activity with a meal close by, another activity within 2 hours, too little sensor data, or glucose already low at the start is left out.",
        "Insulin and meals can't be separated from the effect of activity here.",
        extra
    )

    private fun card(
        c: Ctx, id: String, detector: String, title: String, summary: String, graph: MiniGraph, n: Int, positive: Int?, effect: Double,
        effectLabel: String, uncertainty: String, limits: List<String>, examples: List<String>, tone: InsightTone, priority: Double, baseline: String? = null
    ) = InsightCard(
        id = id, category = InsightCategory.EXERCISE, detector = detector, title = title, summary = summary, miniGraph = graph,
        observationFromUtc = c.from, observationToUtc = c.to, baselineFromUtc = baseline?.let { c.from }, baselineToUtc = baseline?.let { c.to },
        baselineLabel = baseline, sampleSize = n, sampleUnit = "workouts and activity", positiveCount = positive, effectSize = effect,
        effectLabel = effectLabel, uncertainty = uncertainty, limitations = limits, examples = examples, tone = tone, priority = priority
    )

    // ---- single-group findings -----------------------------------------------------------------------------------

    private fun drops(c: Ctx, g: List<Response>, label: String?): InsightCandidate? {
        val med = median(g.map { it.change.toDouble() })
        val dropped = g.count { it.change <= -DROP }
        if (med > -DROP || share(dropped, g.size) < 0.5) return null
        val (a, b) = halves(g)
        if (median(a.map { it.change.toDouble() }) > -DROP * 0.66 || median(b.map { it.change.toDouble() }) > -DROP * 0.66) return null
        val s = scopeLabel(label)
        return InsightCandidate(
            card(
                c, "ex.drop.$s", "ex-drop", "Glucose drops during $s",
                "By the end of $s glucose was typically ${(-med).roundToInt()} mg/dL lower than at the start, and at least ${DROP.toInt()} lower in $dropped of ${g.size}.",
                graph(g, null, s.replaceFirstChar { it.uppercase() }, null), g.size, dropped, -med, "typical drop ${(-med).roundToInt()} mg/dL",
                "The drop was clear in both halves of the period.", limitations("Longer or more intense sessions may drop glucose more; this looks at all of them together."),
                g.sortedBy { it.change }.take(3).map { "${whenLabel(it, c.input.zone)} · ${it.change.roundToInt()} mg/dL" },
                InsightTone.UNFAVORABLE, rank(55.0, (-med - DROP) / 40.0, g.size)
            ),
            TipTopic.EX_DROP
        )
    }

    private fun immediateLows(c: Ctx, g: List<Response>, label: String?): InsightCandidate? {
        val lows = g.filter { it.immediateLow }
        if (lows.size < MIN_LOW_EVENTS || share(lows.size, g.size) < MIN_LOW_SHARE) return null
        val (a, b) = halves(g)
        if (a.none { it.immediateLow } || b.none { it.immediateLow }) return null
        val s = scopeLabel(label); val sh = share(lows.size, g.size)
        return InsightCandidate(
            card(
                c, "ex.low.$s", "ex-low", "Low glucose after $s",
                "Within 2 hours of finishing, glucose went below ${c.t.low} mg/dL after ${lows.size} of ${g.size} $s sessions.",
                graph(g, null, s.replaceFirstChar { it.uppercase() }, null), g.size, lows.size, sh, "${lows.size} of ${g.size} sessions (${pct(sh)})",
                "Seen in both the earlier and the later half of the period.", limitations("A low counts after at least 15 minutes below ${c.t.low} mg/dL."),
                lows.take(3).map { whenLabel(it, c.input.zone) }, InsightTone.UNFAVORABLE, rank(90.0, sh / 0.5, g.size)
            ),
            TipTopic.EX_LOW_NOW, escalate = sh >= 0.4
        )
    }

    private fun delayedLows(c: Ctx, g0: List<Response>, label: String?): InsightCandidate? {
        val g = g0.filter { it.delayedLow != null }
        if (g.size < MIN_EVENTS) return null
        val lows = g.filter { it.delayedLow == true }
        if (lows.size < MIN_LOW_EVENTS || share(lows.size, g.size) < MIN_LOW_SHARE) return null
        val (a, b) = halves(g)
        if (a.none { it.delayedLow == true } || b.none { it.delayedLow == true }) return null
        val s = scopeLabel(label); val sh = share(lows.size, g.size)
        return InsightCandidate(
            card(
                c, "ex.delayed.$s", "ex-delayed", "Low glucose hours after $s",
                "Between 2 and 8 hours after finishing, glucose went below ${c.t.low} mg/dL after ${lows.size} of ${g.size} $s sessions.",
                graph(g, null, s.replaceFirstChar { it.uppercase() }, null), g.size, lows.size, sh, "${lows.size} of ${g.size} sessions (${pct(sh)})",
                "Seen in both the earlier and the later half of the period.",
                limitations("Meals and insulin in the hours after activity can cause lows too; only sessions with no meal within 2 hours are used."),
                lows.take(3).map { whenLabel(it, c.input.zone) }, InsightTone.UNFAVORABLE, rank(85.0, sh / 0.5, g.size)
            ),
            TipTopic.EX_LOW_LATER, escalate = sh >= 0.4
        )
    }

    private fun rises(c: Ctx, g: List<Response>, label: String?): InsightCandidate? {
        val med = median(g.map { it.change.toDouble() })
        val up = g.count { it.change >= DROP }
        if (med < DROP || share(up, g.size) < 0.5) return null
        val s = scopeLabel(label)
        return InsightCandidate(
            card(
                c, "ex.rise.$s", "ex-rise", "Glucose rises during $s",
                "By the end of $s glucose was typically ${med.roundToInt()} mg/dL higher than at the start, and rose by ${DROP.toInt()} or more in $up of ${g.size}.",
                graph(g, null, s.replaceFirstChar { it.uppercase() }, null), g.size, up, med, "typical rise ${med.roundToInt()} mg/dL",
                "The rise was clear across the period.", limitations("Short or intense efforts often raise glucose for a while; it usually settles afterwards."),
                g.sortedByDescending { it.change }.take(3).map { "${whenLabel(it, c.input.zone)} · +${it.change.roundToInt()} mg/dL" },
                InsightTone.MIXED, rank(40.0, (med - DROP) / 40.0, g.size)
            ),
            TipTopic.EX_RISE
        )
    }

    private fun steady(c: Ctx, g: List<Response>, label: String?): InsightCandidate? {
        if (g.size < 8) return null
        val ok = g.count { abs(it.change) < DROP && !it.immediateLow && it.delayedLow != true }
        val med = median(g.map { abs(it.change.toDouble()) })
        val sh = share(ok, g.size)
        if (sh < 0.7 || med >= 20) return null
        val s = scopeLabel(label)
        return InsightCandidate(
            card(
                c, "ex.good.$s", "ex-good", "Steady glucose during $s",
                "During and after $ok of ${g.size} $s sessions glucose stayed within ${DROP.toInt()} mg/dL of its starting level with no lows.",
                graph(g, null, s.replaceFirstChar { it.uppercase() }, null), g.size, ok, sh, "${pct(sh)} of sessions steady",
                "Consistent across the period.", limitations("Steady means a change under ${DROP.toInt()} mg/dL by the end and no low in the 8 hours after."),
                emptyList(), InsightTone.FAVORABLE, rank(30.0, (sh - 0.7) / 0.3, g.size)
            ),
            TipTopic.EX_GOOD
        )
    }

    // ---- comparisons -----------------------------------------------------------------------------------------------

    private fun longVsShort(c: Ctx): List<InsightCandidate> {
        val withMin = c.all.filter { it.minChange != null }
        val (long, short) = withMin.partition { it.event.durationMin >= LONG_MIN }
        if (long.size < MIN_GROUP || short.size < MIN_GROUP) return emptyList()
        val (diff, se) = welch(short.map { it.minChange!!.toDouble() }, long.map { it.minChange!!.toDouble() })
        if (diff < COMPARE_DIFF || diff < SE_FACTOR * se) return emptyList()
        return listOf(
            InsightCandidate(
                card(
                    c, "ex.long", "ex-long", "Lower glucose after long sessions",
                    "In the 8 hours after sessions of $LONG_MIN minutes or more (${long.size}) glucose reached a low point ${diff.roundToInt()} mg/dL lower than after shorter ones (${short.size}).",
                    graph(long, short, "$LONG_MIN min or more", "Shorter"), long.size + short.size, long.size, diff, "${diff.roundToInt()} mg/dL lower low point",
                    margin(diff, se), limitations("Longer sessions are often also harder or later in the day, so duration alone can't explain this."),
                    emptyList(), InsightTone.UNFAVORABLE, rank(50.0, diff / 50.0, long.size + short.size), baseline = "Shorter sessions in the same period"
                ),
                TipTopic.EX_LONG
            )
        )
    }

    private fun evening(c: Ctx): List<InsightCandidate> {
        val withMin = c.all.filter { it.minChange != null }
        fun hour(r: Response) = Instant.ofEpochMilli(r.event.startUtc).atZone(c.input.zone).hour
        val (late, early) = withMin.partition { hour(it) >= EVENING_HOUR }
        if (late.size < MIN_GROUP || early.size < MIN_GROUP) return emptyList()
        val (diff, se) = welch(early.map { it.minChange!!.toDouble() }, late.map { it.minChange!!.toDouble() })
        if (diff < COMPARE_DIFF || diff < SE_FACTOR * se) return emptyList()
        return listOf(
            InsightCandidate(
                card(
                    c, "ex.evening", "ex-evening", "Lower glucose after evening activity",
                    "In the 8 hours after activity that started after $EVENING_HOUR:00 (${late.size}) glucose reached a low point ${diff.roundToInt()} mg/dL lower than after earlier activity (${early.size}).",
                    graph(late, early, "Evening", "Earlier in the day"), late.size + early.size, late.size, diff, "${diff.roundToInt()} mg/dL lower low point",
                    margin(diff, se), limitations("Evening activity runs into the night, when insulin and the body's overnight rhythm also act on glucose."),
                    emptyList(), InsightTone.UNFAVORABLE, rank(50.0, diff / 50.0, late.size + early.size), baseline = "Activity earlier in the day"
                ),
                TipTopic.EX_EVENING
            )
        )
    }

    private fun intensity(c: Ctx): List<InsightCandidate> {
        val withHr = c.all.filter { it.event.meanBpm != null }
        if (withHr.size < 2 * MIN_GROUP) return emptyList()
        val cut = median(withHr.map { it.event.meanBpm!! })
        val (hard, easy) = withHr.partition { it.event.meanBpm!! >= cut }
        if (hard.size < MIN_GROUP || easy.size < MIN_GROUP) return emptyList()
        val (diff, se) = welch(hard.map { it.change.toDouble() }, easy.map { it.change.toDouble() })
        if (abs(diff) < INTENSITY_DIFF || abs(diff) < SE_FACTOR * se) return emptyList()
        val down = diff < 0
        return listOf(
            InsightCandidate(
                card(
                    c, "ex.intensity", "ex-intensity", if (down) "Bigger glucose drop in harder activity" else "Bigger glucose rise in harder activity",
                    "In activity with a higher heart rate (above ${cut.roundToInt()} bpm on average, ${hard.size} sessions) glucose changed ${abs(diff).roundToInt()} mg/dL ${if (down) "more downwards" else "more upwards"} by the end than in easier activity (${easy.size}).",
                    graph(hard, easy, "Higher heart rate", "Lower heart rate"), hard.size + easy.size, hard.size, abs(diff), "${abs(diff).roundToInt()} mg/dL difference",
                    margin(diff, se), limitations("Heart rate also reflects heat, caffeine and fitness, so it's only a rough guide to effort."),
                    emptyList(), InsightTone.MIXED, rank(40.0, abs(diff) / 50.0, hard.size + easy.size), baseline = "Activity with a lower heart rate"
                ),
                TipTopic.EX_INTENSITY
            )
        )
    }

    /** Active days (30 min or more of activity) vs quieter days, by how much of the next day was spent in range. */
    private fun nextDay(input: DetectionInput): List<InsightCandidate> {
        val t = input.thresholds
        val whole = 0 until BINS_PER_DAY
        val byDate = input.days.associateBy { it.date }
        val minutesByDay = input.events.groupBy { Instant.ofEpochMilli(it.startUtc).atZone(input.zone).toLocalDate() }
            .mapValues { (_, e) -> e.sumOf { it.durationMin } }
        val first = input.today.minusDays((WINDOW_DAYS - 1).toLong())
        val pairs = generateSequence(first) { it.plusDays(1) }.takeWhile { it < input.today.minusDays(1) }
            .mapNotNull { d ->
                val next = byDate[d.plusDays(1)]?.takeIf { it.coverage(whole) >= MIN_COVERAGE && d.plusDays(1) < input.today } ?: return@mapNotNull null
                val v = next.bins.filter { !it.isNaN() }
                Triple(d, next, share(v.count { it >= t.low && it <= t.high }, v.size) * 100)
            }.toList()
        val (active, quiet) = pairs.partition { (minutesByDay[it.first] ?: 0) >= ACTIVE_DAY_MIN }
        if (active.size < MIN_DAYS_PER_GROUP || quiet.size < MIN_DAYS_PER_GROUP) return emptyList()
        val (diff, se) = welch(active.map { it.third }, quiet.map { it.third })
        if (diff < NEXT_DAY_TIR_DIFF || diff < SE_FACTOR * se) return emptyList()
        val agpActive = agpOfDays(active.map { it.second }) ?: return emptyList()
        val agpQuiet = agpOfDays(quiet.map { it.second }) ?: return emptyList()
        val from = input.startOfDayUtc(first)
        return listOf(
            InsightCandidate(
                InsightCard(
                    id = "ex.nextday", category = InsightCategory.EXERCISE, detector = "ex-nextday", title = "Steadier day after active days",
                    summary = "Time in range was ${active.map { it.third }.average().roundToInt()} % the day after $ACTIVE_DAY_MIN+ active minutes (${active.size} days) and ${quiet.map { it.third }.average().roundToInt()} % after quieter days (${quiet.size}).",
                    miniGraph = MiniGraph.ProfileComparison("After active days", agpActive.medianGraph(), "After quieter days", agpQuiet.medianGraph()),
                    observationFromUtc = from, observationToUtc = input.nowUtc, baselineFromUtc = from, baselineToUtc = input.nowUtc,
                    baselineLabel = "Days after quieter days", sampleSize = active.size + quiet.size, sampleUnit = "days", positiveCount = active.size,
                    effectSize = diff, effectLabel = "+${diff.roundToInt()} points in range",
                    uncertainty = "The difference is about ${"%.1f".format(diff / se.coerceAtLeast(0.01))}× its likely margin of error (±${se.roundToInt()} points).",
                    limitations = listOf(
                        "Days without activity data (for example a watch left at home) count as quiet days.",
                        "Active days differ in other ways too, such as meals, sleep and routine, so this can't show that activity is the reason."
                    ),
                    tone = InsightTone.FAVORABLE, priority = rank(30.0, diff / 20.0, active.size + quiet.size)
                ),
                TipTopic.EX_NEXTDAY
            )
        )
    }
}
