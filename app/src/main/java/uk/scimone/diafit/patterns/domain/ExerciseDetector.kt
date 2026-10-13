package uk.scimone.diafit.patterns.domain

import java.time.Instant
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

    internal const val FIRST_K = -6
    internal const val LAST_K = 96 // start + 8 h
    private const val GRAPH_STEP = 3

    private const val DROP = 30.0
    /** A session counts as long when it is at least as long as the user's median one, and clearly longer than the rest. */
    private const val LONG_RATIO = 1.3
    private const val LONG_MIN_GAP_MIN = 10
    private const val EVENING_HOUR = 17
    private const val COMPARE_DIFF = 20.0
    private const val INTENSITY_DIFF = 25.0
    private const val SE_FACTOR = 2.0
    private const val MIN_ACTIVE_DAY_MIN = 15
    /** Only a meal this big, starting right around the activity, is allowed to muddy its response. */
    private const val MEAL_CARBS_THAT_MATTER = 20
    private const val NEXT_DAY_TIR_DIFF = 8.0
    private const val MIN_DAYS_PER_GROUP = 6

    /** One event's measured glucose response; null fields = not measurable for this event. */
    class Response(
        val event: ActivityEvent, val baseline: Float, val values: FloatArray, val change: Float,
        val immediateLow: Boolean, val delayedLow: Boolean?, val lowestAfter: Float?, val lowestAt: Int?
    ) {
        val minChange: Float? get() = lowestAfter?.let { it - baseline }

        /** Lowest recorded glucose [fromMin]..[toMin] after the start (within the measured 8 h), with its time. */
        fun lowestBetween(fromMin: Int, toMin: Int): Pair<Long, Float>? =
            ((fromMin / BIN_MINUTES)..minOf(toMin / BIN_MINUTES, LAST_K)).filter { it - FIRST_K in values.indices && !values[it - FIRST_K].isNaN() }
                .minByOrNull { values[it - FIRST_K] }?.let { event.startUtc + it * BIN_MINUTES * 60_000L to values[it - FIRST_K] }

        /**
         * This activity as an example: from 30 min before the start to [afterMin] after its end, with
         * [focusFromMin]..[focusToMin] after the start shaded.
         */
        fun case(afterMin: Int, focusFromMin: Int, focusToMin: Int, note: Msg, markUtc: Long? = null, group: Msg? = null) =
            Cases.event(
                event.startUtc, event.endUtc, msg("pat_ex_event", event.label, Cases.duration(event.durationMin)), 30, afterMin,
                focusFromMin, focusToMin, note, markUtc, group
            )

        /** Shaded over the activity itself, noting the change by its end. */
        fun duringCase(group: Msg? = null, bpm: Int? = null) =
            case(
                120, 0, event.durationMin,
                if (bpm != null) msg("pat_ex_during_case_bpm", Cases.signed(change), bpm) else msg("pat_ex_during_case", Cases.signed(change)),
                group = group
            )

        /** Shaded over the 8 hours after, marking the lowest point. */
        fun afterCase(group: Msg? = null): PatternExample {
            val low = lowestBetween(event.durationMin, event.durationMin + 480)
            return case(
                480, event.durationMin, event.durationMin + 480,
                low?.let { msg("pat_ex_low_case", it.second.roundToInt(), Cases.duration(((it.first - event.endUtc) / 60_000L).toInt().coerceAtLeast(0))) } ?: msg("pat_ex_no_low"),
                low?.first, group
            )
        }
    }

    fun detect(input: DetectionInput): List<PatternCandidate> {
        val responses = responses(input)
        val out = ArrayList<PatternCandidate>()
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
        // Effect-size thresholds follow this person's glucose variability.
        val drop = input.scaled(DROP)
        val compareDiff = input.scaled(COMPARE_DIFF)
        val intensityDiff = input.scaled(INTENSITY_DIFF)
    }

    // -------------------------------------------------------------------------------------------------------------

    internal fun responses(input: DetectionInput, windowDays: Int = WINDOW_DAYS): List<Response> {
        val lookup = GlucoseLookup(input.days, input.zone)
        val low = input.thresholds.low
        val since = input.startOfDayUtc(input.today.minusDays((windowDays - 1).toLong()))
        val events = input.events.sortedBy { it.startUtc }
        val min = 60_000L
        val step = BIN_MINUTES * min
        val out = ArrayList<Response>()
        for ((i, e) in events.withIndex()) {
            if (e.startUtc < since || e.durationMin < MIN_EVENT_MIN || e.endUtc + 120 * min > input.nowUtc) continue
            if (events.any { it !== e && ((it.startUtc > e.startUtc && it.startUtc <= e.endUtc + 120 * min) || (it.endUtc >= e.startUtc - 120 * min && it.endUtc < e.startUtc)) }) continue
            if (input.sittings.any { it.totalCarbs >= MEAL_CARBS_THAT_MATTER && it.startTime in (e.startUtc - 30 * min)..(e.endUtc + 45 * min) }) continue

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

    /** The scope as it appears in stored ids (stable, English). */
    private fun scopeLabel(label: String?) = label?.let { if (it.startsWith("@")) "activity" else it.lowercase() } ?: "activity"

    /** The scope as a noun in a sentence: a named workout kind in lower case, else the word "activity". */
    private fun scopeText(label: String?) = if (label == null || label.startsWith("@")) ref("pat_scope_activity") else label.lowercase()

    /** The scope as a graph label. */
    private fun scopeGraphLabel(label: String?) =
        if (label == null || label.startsWith("@")) Msg("pat_scope_activity_label") else msg("pat_literal", label.replaceFirstChar { it.uppercase() })

    /** The whole set first; only when that finds nothing, each kind of workout with enough events on its own. */
    private fun scoped(c: Ctx, eval: (Ctx, List<Response>, String?) -> PatternCandidate?): List<PatternCandidate> {
        eval(c, c.all, null)?.let { return listOf(it) }
        return c.all.groupBy { it.event.label }.mapNotNull { (label, g) -> if (g.size < MIN_EVENTS) null else eval(c, g, label) }
    }

    private fun halves(g: List<Response>) = g.sortedBy { it.event.startUtc }.let { it.take(it.size / 2) to it.drop(it.size / 2) }
    private fun median(x: List<Double>) = x.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
    private fun share(n: Int, d: Int) = if (d == 0) 0.0 else n.toDouble() / d
    private fun pct(x: Double) = "${(x * 100).roundToInt()} %"
    private fun variance(x: List<Double>) = if (x.size < 2) 0.0 else x.sumOf { (it - x.average()).let { d -> d * d } } / (x.size - 1)
    private fun welch(a: List<Double>, b: List<Double>) = (a.average() - b.average()) to sqrt(variance(a) / a.size + variance(b) / b.size)
    private fun margin(diff: Double, se: Double) = msg("pat_meal_margin", "%.1f".format(java.util.Locale.ROOT, abs(diff) / se.coerceAtLeast(0.01)), se.roundToInt())

    private fun rank(base: Double, effect: Double, n: Int) = base * (0.6 + 0.4 * effect.coerceIn(0.0, 1.0)) * (0.7 + 0.3 * (n / 15.0).coerceAtMost(1.0))

    private fun graph(g: List<Response>, compare: List<Response>?, aLabel: Msg, bLabel: Msg?): MiniGraph =
        eventGraph(
            g.map { EventCurve(it.values, it.baseline) }, compare?.map { EventCurve(it.values, it.baseline) },
            FIRST_K, LAST_K, GRAPH_STEP, aLabel, bLabel, Msg(EVENT_START), median(g.map { it.event.durationMin.toDouble() }).roundToInt(),
            listOf(0, 120, 240, 360, 480)
        )

    private fun limitations(extra: Msg) = listOf(msg("pat_ex_lim_excluded"), msg("pat_ex_lim_confound"), extra)

    private fun card(
        c: Ctx, id: String, detector: String, title: Msg, summary: Msg, graph: MiniGraph, n: Int, positive: Int?, effect: Double,
        effectLabel: Msg, uncertainty: Msg, limits: List<Msg>, examples: List<PatternExample>, tone: PatternTone, priority: Double, baseline: Msg? = null,
        countLabel: Boolean = false
    ) = PatternCard(
        id = id, category = PatternCategory.EXERCISE, detector = detector, title = title, summary = summary, miniGraph = graph,
        observationFromUtc = c.from, observationToUtc = c.to, baselineFromUtc = baseline?.let { c.from }, baselineToUtc = baseline?.let { c.to },
        baselineLabel = baseline, sampleSize = n, sampleUnit = SampleUnit.ACTIVITY, positiveCount = positive, effectSize = effect,
        effectLabel = effectLabel, effectIsCount = countLabel, uncertainty = uncertainty, limitations = limits, cases = examples, tone = tone, priority = priority
    )

    // ---- single-group findings -----------------------------------------------------------------------------------

    private fun drops(c: Ctx, g: List<Response>, label: String?): PatternCandidate? {
        val med = median(g.map { it.change.toDouble() })
        val dropped = g.count { it.change <= -c.drop }
        if (med > -c.drop || share(dropped, g.size) < 0.5) return null
        val (a, b) = halves(g)
        if (median(a.map { it.change.toDouble() }) > -c.drop * 0.66 || median(b.map { it.change.toDouble() }) > -c.drop * 0.66) return null
        val s = scopeLabel(label); val st = scopeText(label)
        return PatternCandidate(
            card(
                c, "ex.drop.$s", "ex-drop", msg("pat_ex_drop_title", st),
                msg("pat_ex_drop_summary", st, (-med).roundToInt(), c.drop.roundToInt(), dropped, g.size),
                graph(g, null, scopeGraphLabel(label), null), g.size, dropped, -med, msg("pat_ex_drop_effect", (-med).roundToInt()),
                msg("pat_ex_drop_unc"), limitations(msg("pat_ex_drop_lim")),
                g.sortedBy { it.change }.take(Cases.SINGLE).map { it.duringCase() },
                PatternTone.UNFAVORABLE, rank(55.0, (-med - c.drop) / 40.0, g.size)
            ),
            TipTopic.EX_DROP
        )
    }

    private fun immediateLows(c: Ctx, g: List<Response>, label: String?): PatternCandidate? {
        val lows = g.filter { it.immediateLow }
        if (lows.size < MIN_LOW_EVENTS || share(lows.size, g.size) < MIN_LOW_SHARE) return null
        val (a, b) = halves(g)
        if (a.none { it.immediateLow } || b.none { it.immediateLow }) return null
        val s = scopeLabel(label); val st = scopeText(label); val sh = share(lows.size, g.size)
        return PatternCandidate(
            card(
                c, "ex.low.$s", "ex-low", msg("pat_ex_low_title", st),
                msg("pat_ex_low_summary", c.t.low, lows.size, g.size, st),
                graph(g, null, scopeGraphLabel(label), null), g.size, lows.size, sh, msg("pat_ex_count_effect", lows.size, g.size, pct(sh)),
                msg("pat_meal_unc_halves"), limitations(msg("pat_ex_low_lim", c.t.low)),
                lows.mapNotNull { r -> r.lowestBetween(r.event.durationMin, r.event.durationMin + 120)?.let { r to it } }.sortedBy { it.second.second }
                    .take(Cases.SINGLE).map { (r, low) ->
                        r.case(
                            120, r.event.durationMin, r.event.durationMin + 120,
                            msg("pat_ex_low_case", low.second.roundToInt(), Cases.duration(((low.first - r.event.endUtc) / 60_000L).toInt().coerceAtLeast(0))), low.first
                        )
                    },
                PatternTone.UNFAVORABLE, rank(90.0, sh / 0.5, g.size), countLabel = true
            ),
            TipTopic.EX_LOW_NOW, escalate = sh >= 0.4
        )
    }

    private fun delayedLows(c: Ctx, g0: List<Response>, label: String?): PatternCandidate? {
        val g = g0.filter { it.delayedLow != null }
        if (g.size < MIN_EVENTS) return null
        val lows = g.filter { it.delayedLow == true }
        if (lows.size < MIN_LOW_EVENTS || share(lows.size, g.size) < MIN_LOW_SHARE) return null
        val (a, b) = halves(g)
        if (a.none { it.delayedLow == true } || b.none { it.delayedLow == true }) return null
        val s = scopeLabel(label); val st = scopeText(label); val sh = share(lows.size, g.size)
        return PatternCandidate(
            card(
                c, "ex.delayed.$s", "ex-delayed", msg("pat_ex_delayed_title", st),
                msg("pat_ex_delayed_summary", c.t.low, lows.size, g.size, st),
                graph(g, null, scopeGraphLabel(label), null), g.size, lows.size, sh, msg("pat_ex_count_effect", lows.size, g.size, pct(sh)),
                msg("pat_meal_unc_halves"),
                limitations(msg("pat_ex_delayed_lim")),
                lows.mapNotNull { r -> r.lowestBetween(r.event.durationMin + 120, r.event.durationMin + 480)?.let { r to it } }.sortedBy { it.second.second }
                    .take(Cases.SINGLE).map { (r, low) ->
                        r.case(
                            480, r.event.durationMin + 120, r.event.durationMin + 480,
                            msg("pat_ex_low_case", low.second.roundToInt(), Cases.duration(((low.first - r.event.endUtc) / 60_000L).toInt())), low.first
                        )
                    },
                PatternTone.UNFAVORABLE, rank(85.0, sh / 0.5, g.size), countLabel = true
            ),
            TipTopic.EX_LOW_LATER, escalate = sh >= 0.4
        )
    }

    private fun rises(c: Ctx, g: List<Response>, label: String?): PatternCandidate? {
        val med = median(g.map { it.change.toDouble() })
        val up = g.count { it.change >= c.drop }
        if (med < c.drop || share(up, g.size) < 0.5) return null
        val s = scopeLabel(label); val st = scopeText(label)
        return PatternCandidate(
            card(
                c, "ex.rise.$s", "ex-rise", msg("pat_ex_rise_title", st),
                msg("pat_ex_rise_summary", st, med.roundToInt(), c.drop.roundToInt(), up, g.size),
                graph(g, null, scopeGraphLabel(label), null), g.size, up, med, msg("pat_ex_rise_effect", med.roundToInt()),
                msg("pat_ex_rise_unc"), limitations(msg("pat_ex_rise_lim")),
                g.sortedByDescending { it.change }.take(Cases.SINGLE).map { it.duringCase() },
                PatternTone.MIXED, rank(40.0, (med - c.drop) / 40.0, g.size)
            ),
            TipTopic.EX_RISE
        )
    }

    private fun steady(c: Ctx, g: List<Response>, label: String?): PatternCandidate? {
        if (g.size < 8) return null
        val ok = g.count { abs(it.change) < c.drop && !it.immediateLow && it.delayedLow != true }
        val med = median(g.map { abs(it.change.toDouble()) })
        val sh = share(ok, g.size)
        if (sh < 0.7 || med >= 20) return null
        val s = scopeLabel(label); val st = scopeText(label)
        return PatternCandidate(
            card(
                c, "ex.good.$s", "ex-good", msg("pat_ex_good_title", st),
                msg("pat_ex_good_summary", ok, g.size, st, c.drop.roundToInt()),
                graph(g, null, scopeGraphLabel(label), null), g.size, ok, sh, msg("pat_ex_good_effect", pct(sh)),
                msg("pat_meal_good_unc"), limitations(msg("pat_ex_good_lim", c.drop.roundToInt())),
                Cases.typical(g.filter { abs(it.change) < c.drop && !it.immediateLow && it.delayedLow != true }, Cases.SINGLE) { it.change.toDouble() }
                    .sortedByDescending { it.event.startUtc }.map { it.duringCase() },
                PatternTone.FAVORABLE, rank(30.0, (sh - 0.7) / 0.3, g.size)
            ),
            TipTopic.EX_GOOD
        )
    }

    // ---- comparisons -----------------------------------------------------------------------------------------------

    private fun longVsShort(c: Ctx): List<PatternCandidate> {
        val withMin = c.all.filter { it.minChange != null }
        if (withMin.size < 2 * MIN_GROUP) return emptyList()
        val cut = median(withMin.map { it.event.durationMin.toDouble() }).roundToInt()
        val (long, short) = withMin.partition { it.event.durationMin >= cut }
        if (long.size < MIN_GROUP || short.size < MIN_GROUP) return emptyList()
        val longTypical = median(long.map { it.event.durationMin.toDouble() }); val shortTypical = median(short.map { it.event.durationMin.toDouble() })
        if (longTypical < LONG_RATIO * shortTypical || longTypical - shortTypical < LONG_MIN_GAP_MIN) return emptyList() // no clear long/short split in this person's sessions
        val (diff, se) = welch(short.map { it.minChange!!.toDouble() }, long.map { it.minChange!!.toDouble() })
        if (diff < c.compareDiff || diff < SE_FACTOR * se) return emptyList()
        return listOf(
            PatternCandidate(
                card(
                    c, "ex.long", "ex-long", msg("pat_ex_long_title"),
                    msg("pat_ex_long_summary", cut, long.size, diff.roundToInt(), short.size),
                    graph(long, short, msg("pat_label_min_or_more", cut), Msg("pat_label_shorter")), long.size + short.size, long.size, diff, msg("pat_ex_lowpoint_effect", diff.roundToInt()),
                    margin(diff, se), limitations(msg("pat_ex_long_lim", cut)),
                    Cases.typical(long) { it.minChange!!.toDouble() }.map { it.afterCase(msg("pat_label_min_or_more", cut)) } +
                        Cases.typical(short) { it.minChange!!.toDouble() }.map { it.afterCase(Msg("pat_label_shorter")) },
                    PatternTone.UNFAVORABLE, rank(50.0, diff / 50.0, long.size + short.size), baseline = msg("pat_ex_long_baseline")
                ),
                TipTopic.EX_LONG
            )
        )
    }

    private fun evening(c: Ctx): List<PatternCandidate> {
        val withMin = c.all.filter { it.minChange != null }
        fun hour(r: Response) = Instant.ofEpochMilli(r.event.startUtc).atZone(c.input.zone).hour
        val (late, early) = withMin.partition { hour(it) >= EVENING_HOUR }
        if (late.size < MIN_GROUP || early.size < MIN_GROUP) return emptyList()
        val (diff, se) = welch(early.map { it.minChange!!.toDouble() }, late.map { it.minChange!!.toDouble() })
        if (diff < c.compareDiff || diff < SE_FACTOR * se) return emptyList()
        return listOf(
            PatternCandidate(
                card(
                    c, "ex.evening", "ex-evening", msg("pat_ex_evening_title"),
                    msg("pat_ex_evening_summary", EVENING_HOUR, late.size, diff.roundToInt(), early.size),
                    graph(late, early, Msg("pat_label_evening"), Msg("pat_label_earlier_day")), late.size + early.size, late.size, diff, msg("pat_ex_lowpoint_effect", diff.roundToInt()),
                    margin(diff, se), limitations(msg("pat_ex_evening_lim")),
                    Cases.typical(late) { it.minChange!!.toDouble() }.map { it.afterCase(Msg("pat_label_evening")) } +
                        Cases.typical(early) { it.minChange!!.toDouble() }.map { it.afterCase(Msg("pat_label_earlier_day")) },
                    PatternTone.UNFAVORABLE, rank(50.0, diff / 50.0, late.size + early.size), baseline = msg("pat_ex_evening_baseline")
                ),
                TipTopic.EX_EVENING
            )
        )
    }

    private fun intensity(c: Ctx): List<PatternCandidate> {
        val withHr = c.all.filter { it.event.meanBpm != null }
        if (withHr.size < 2 * MIN_GROUP) return emptyList()
        val cut = median(withHr.map { it.event.meanBpm!! })
        val (hard, easy) = withHr.partition { it.event.meanBpm!! >= cut }
        if (hard.size < MIN_GROUP || easy.size < MIN_GROUP) return emptyList()
        val (diff, se) = welch(hard.map { it.change.toDouble() }, easy.map { it.change.toDouble() })
        if (abs(diff) < c.intensityDiff || abs(diff) < SE_FACTOR * se) return emptyList()
        val down = diff < 0
        return listOf(
            PatternCandidate(
                card(
                    c, "ex.intensity", "ex-intensity", msg(if (down) "pat_ex_int_title_down" else "pat_ex_int_title_up"),
                    msg(if (down) "pat_ex_int_summary_down" else "pat_ex_int_summary_up", cut.roundToInt(), hard.size, abs(diff).roundToInt(), easy.size),
                    graph(hard, easy, Msg("pat_label_hr_high"), Msg("pat_label_hr_low")), hard.size + easy.size, hard.size, abs(diff), msg("pat_ex_int_effect", abs(diff).roundToInt()),
                    margin(diff, se), limitations(msg("pat_ex_int_lim")),
                    Cases.typical(hard) { it.change.toDouble() }.map { it.duringCase(Msg("pat_label_hr_high"), it.event.meanBpm!!.roundToInt()) } +
                        Cases.typical(easy) { it.change.toDouble() }.map { it.duringCase(Msg("pat_label_hr_low"), it.event.meanBpm!!.roundToInt()) },
                    PatternTone.MIXED, rank(40.0, abs(diff) / 50.0, hard.size + easy.size), baseline = msg("pat_ex_int_baseline")
                ),
                TipTopic.EX_INTENSITY
            )
        )
    }

    /** Active days (30 min or more of activity) vs quieter days, by how much of the next day was spent in range. */
    private fun nextDay(input: DetectionInput): List<PatternCandidate> {
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
        val activeDayMinutes = minutesByDay.values.filter { it > 0 }.sorted()
        if (activeDayMinutes.size < MIN_DAYS_PER_GROUP) return emptyList()
        // An active day is a typical active day for this person (at least their median amount, never under 15 min).
        val cutMin = maxOf(MIN_ACTIVE_DAY_MIN, activeDayMinutes[activeDayMinutes.size / 2])
        val (active, quiet) = pairs.partition { (minutesByDay[it.first] ?: 0) >= cutMin }
        if (active.size < MIN_DAYS_PER_GROUP || quiet.size < MIN_DAYS_PER_GROUP) return emptyList()
        val (diff, se) = welch(active.map { it.third }, quiet.map { it.third })
        if (diff < NEXT_DAY_TIR_DIFF || diff < SE_FACTOR * se) return emptyList()
        val agpActive = agpOfDays(active.map { it.second }) ?: return emptyList()
        val agpQuiet = agpOfDays(quiet.map { it.second }) ?: return emptyList()
        val from = input.startOfDayUtc(first)
        // A typical day after an active day and after a quieter day, each drawn with the evening before.
        fun ex(p: Triple<java.time.LocalDate, DayGlucose, Double>, group: Msg) = Cases.window(
            input, p.second.date, 0 until BINS_PER_DAY, 8 * 60, 0,
            msg("pat_ex_nextday_case", p.third.roundToInt(), minutesByDay[p.first] ?: 0), group = group
        )
        val examples = Cases.typical(active) { it.third }.map { ex(it, Msg("pat_label_after_active")) } + Cases.typical(quiet) { it.third }.map { ex(it, Msg("pat_label_after_quiet")) }
        return listOf(
            PatternCandidate(
                PatternCard(
                    id = "ex.nextday", category = PatternCategory.EXERCISE, detector = "ex-nextday", title = msg("pat_ex_nextday_title"),
                    summary = msg("pat_ex_nextday_summary", active.map { it.third }.average().roundToInt(), cutMin, active.size, quiet.map { it.third }.average().roundToInt(), quiet.size),
                    miniGraph = MiniGraph.ProfileComparison(Msg("pat_label_after_active"), agpActive.medianGraph(), Msg("pat_label_after_quiet"), agpQuiet.medianGraph()),
                    observationFromUtc = from, observationToUtc = input.nowUtc, baselineFromUtc = from, baselineToUtc = input.nowUtc,
                    baselineLabel = msg("pat_ex_nextday_baseline"), sampleSize = active.size + quiet.size, sampleUnit = SampleUnit.DAYS, positiveCount = active.size,
                    effectSize = diff, effectLabel = msg("pat_ex_nextday_effect", diff.roundToInt()),
                    uncertainty = msg("pat_tod_weekend_unc", "%.1f".format(java.util.Locale.ROOT, diff / se.coerceAtLeast(0.01)), se.roundToInt()),
                    limitations = listOf(msg("pat_ex_nextday_lim1"), msg("pat_ex_nextday_lim2")),
                    cases = examples,
                    tone = PatternTone.FAVORABLE, priority = rank(30.0, diff / 20.0, active.size + quiet.size)
                ),
                TipTopic.EX_NEXTDAY
            )
        )
    }
}
