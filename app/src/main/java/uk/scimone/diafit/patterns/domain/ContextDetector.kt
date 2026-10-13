package uk.scimone.diafit.patterns.domain

import uk.scimone.diafit.core.domain.model.MealSitting
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Category F, second set: the *context* of an event decides how glucose responds. Each detector below answers a
 * question no existing detector asks, reuses the response measurements of [MealDetector] / [ExerciseDetector] and
 * is held to a stricter bar than a single-factor pattern (groups of >= 6, effect >= 2.5x its standard error, same
 * direction in both halves of the period, comparable groups). All findings are associations; insulin is never used
 * as an explanation or in a tip, since doses follow glucose.
 *
 * What each one adds (and what it deliberately is not):
 *  - ctx.expre      Activity outcome by the glucose *direction at the start* (falling / steady / rising over the last
 *                   30 min). [ExerciseDetector] only pools all activities, or splits by length, time of day, heart rate.
 *  - ctx.exgap      Activity outcome by *time since the last meal*. [ExerciseDetector] only leaves out meals right
 *                   around the activity; it never compares gaps.
 *  - ctx.extype     One logged workout type against the other workouts. [ExerciseDetector] only judges a type on its
 *                   own, never against the others.
 *  - ctx.mealdelay  *Second rise* 2.5-4 h after a meal (shape, not level) in meals matched on carbohydrates and split
 *                   by recorded fat + protein. [MealDetector.rich] compares the *level* at 3-4 h across all meals
 *                   unmatched, and can also use logged absorption. Surfaced only when `meal.rich` is not (see
 *                   [PatternPipeline]), and it never falls back to logged absorption. The "early vs late response
 *                   shape" idea is merged here: the second rise *is* the late shape.
 *  - ctx.mealtime   Rise after carbohydrate-matched meals at different *clock times*. `meal.type` compares the label
 *                   the user chose, unmatched; this one replaces it when it fires (it is the more informative card).
 *                   Also covers "which meal context goes with smoother responses" (the favourable direction).
 *  - ctx.mealweekend Rise after matched meals on weekends vs weekdays. The weekday/weekend time-of-day pattern
 *                   compares overall glucose profiles, not meal responses.
 *  - ctx.mealactivity Rise after matched meals that follow activity within 3 h. No existing detector links the two.
 *  - ctx.night      Overnight (00-06) time in range after active vs quieter days, leaving out days with activity in the
 *                   evening (that is `combo.eveninglow`) and measuring the night, not the next day (`ex.nextday`).
 *
 * Not built, on purpose: sleep duration + breakfast (already `combo.shortbreakfast`), sleep x previous-day activity
 * and late dinner x composition (28 days cannot fill four cells with enough nights), overnight heart rate + glucose
 * (no safe, useful tips can be written for a co-occurrence), sleep-context combinations (same sample problem).
 */
object ContextDetector {
    const val WINDOW_DAYS = LONG_WINDOW_DAYS

    private const val MIN_GROUP = 6
    private const val SE_STRONG = 2.5
    private const val SIMILAR_CARBS = 0.35
    private const val CARBS_RATIO = 1.25
    private const val COMPOSITION_RATIO = 1.4

    private const val PRE_TREND = 15.0
    private const val PRE_DIFF = 25.0
    private const val GAP_DIFF = 20.0
    private const val GAP_MIN_APART = 60
    private const val TYPE_DIFF = 30.0
    private const val SECOND_RISE_DIFF = 20.0
    private const val RICH_MIN_CUT_G = 10
    private const val RICH_RATIO = 1.3
    private const val MEAL_DIFF = 25.0
    private const val ACTIVITY_BEFORE_MEAL_H = 3
    private const val MIN_ACTIVITY_MIN = 15
    private const val NIGHT_TIR_DIFF = 10.0
    private const val NIGHT_END_BIN = 6 * BINS_PER_HOUR
    private const val MIN_COVERAGE = 0.7
    private const val MIN_ACTIVE_DAY_MIN = 15
    /** Multi-factor comparisons (several conditions at once) need this much evidence for the interaction itself. */
    private const val INTERACTION_SE = 3.0
    private const val SLEEP_TIR_DIFF = 10.0
    private const val SLEEP_GAP_MIN = 60
    private const val GOOD_NIGHTS = 8
    private const val REGULAR_BED_MIN = 60
    private const val LATE_GAP_MIN = 60
    private const val LATE_RICH_DIFF = 20.0

    fun detect(input: DetectionInput): List<PatternCandidate> {
        val c = Ctx(input)
        val meals = MealDetector.responses(input, WINDOW_DAYS)
        val ex = ExerciseDetector.responses(input, WINDOW_DAYS)
        return listOfNotNull(
            exPre(c, ex), exGap(c, ex), exType(c, ex),
            mealDelay(c, meals), mealTime(c, meals), mealWeekend(c, meals), mealActivity(c, meals),
            night(c), sleepActivity(c), sleepGood(c), lateDinnerRich(c)
        )
    }

    private class Ctx(val input: DetectionInput) {
        val from = input.startOfDayUtc(input.today.minusDays((WINDOW_DAYS - 1).toLong()))
        val zone = input.zone
    }

    // ---- statistics ---------------------------------------------------------------------------------------------

    private fun median(x: List<Double>) = x.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
    private fun variance(x: List<Double>) = if (x.size < 2) 0.0 else x.sumOf { (it - x.average()).let { d -> d * d } } / (x.size - 1)
    private fun pct(x: Double) = "${x.roundToInt()} %"

    private class Cmp<T>(val hit: List<T>, val rest: List<T>, val diff: Double, val se: Double) {
        val z: Double get() = abs(diff) / se.coerceAtLeast(0.01)
    }

    private fun <T> cmp(hit: List<T>, rest: List<T>, v: (T) -> Double): Cmp<T> {
        val a = hit.map(v); val b = rest.map(v)
        return Cmp(hit, rest, a.average() - b.average(), sqrt(variance(a) / a.size + variance(b) / b.size))
    }

    /** Large enough, clearly beyond the noise, and the same direction in the earlier and the later half. */
    private fun <T> solid(c: Cmp<T>, minDiff: Double, time: (T) -> Long, v: (T) -> Double): Boolean {
        if (c.hit.size < MIN_GROUP || c.rest.size < MIN_GROUP || abs(c.diff) < minDiff || abs(c.diff) < SE_STRONG * c.se) return false
        val mid = (c.hit + c.rest).map(time).sorted().let { it[it.size / 2] }
        return listOf<(T) -> Boolean>({ time(it) < mid }, { time(it) >= mid }).all { half ->
            val h = c.hit.filter(half); val r = c.rest.filter(half)
            h.size < 2 || r.size < 2 || sign(h.map(v).average() - r.map(v).average()) == sign(c.diff)
        }
    }

    private fun margin(c: Cmp<*>, unit: String) =
        msg(if (unit == "mg/dL") "pat_combo_margin" else "pat_sleep_unc", "%.1f".format(java.util.Locale.ROOT, c.z), c.se.roundToInt())

    private fun rank(base: Double, z: Double, n: Int) = base * (0.6 + 0.4 * (z / 6.0).coerceIn(0.0, 1.0)) * (0.7 + 0.3 * (n / 24.0).coerceAtMost(1.0))

    private fun card(
        c: Ctx, id: String, detector: String, title: Msg, summary: Msg, graph: MiniGraph, unit: String, cmp: Cmp<*>, effectLabel: Msg,
        uncertainty: Msg, limits: List<Msg>, examples: List<PatternExample>, tone: PatternTone, priority: Double, baseline: Msg,
        topic: TipTopic, escalate: Boolean = false
    ) = PatternCandidate(
        PatternCard(
            id = id, category = PatternCategory.COMBINED, detector = detector, title = title, summary = summary, miniGraph = graph,
            observationFromUtc = c.from, observationToUtc = c.input.nowUtc, baselineFromUtc = c.from, baselineToUtc = c.input.nowUtc, baselineLabel = baseline,
            sampleSize = cmp.hit.size + cmp.rest.size, sampleUnit = unit, positiveCount = cmp.hit.size, effectSize = abs(cmp.diff), effectLabel = effectLabel,
            uncertainty = uncertainty, limitations = limits, cases = examples, tone = tone, priority = priority
        ),
        topic, escalate
    )

    // ---- exercise ---------------------------------------------------------------------------------------------------

    private fun exGraph(hit: List<ExerciseDetector.Response>, rest: List<ExerciseDetector.Response>, a: Msg, b: Msg): MiniGraph =
        eventGraph(
            hit.map { EventCurve(it.values, it.baseline) }, rest.map { EventCurve(it.values, it.baseline) },
            ExerciseDetector.FIRST_K, ExerciseDetector.LAST_K, 3, a, b, Msg(EVENT_START), median(hit.map { it.event.durationMin.toDouble() }).roundToInt(),
            listOf(0, 120, 240, 360, 480)
        )

    private val exLimits = listOf(msg("pat_ctx_ex_lim1"), msg("pat_ctx_ex_lim2"))

    /** Typical activities of both groups, shaded over the activity (and the [preMin] before it), noting the change by the end. */
    private fun <T> exCases(
        hit: List<T>, rest: List<T>, a: Msg, b: Msg, r: (T) -> ExerciseDetector.Response, preMin: Int = 0, note: ((T, ExerciseDetector.Response) -> Msg)? = null
    ): List<PatternExample> {
        fun ex(item: T, group: Msg) = r(item).let { x ->
            x.case(120, -preMin, x.event.durationMin, note?.invoke(item, x) ?: msg("pat_ex_during_case", Cases.signed(x.change)), group = group)
        }
        return Cases.typical(hit) { r(it).change.toDouble() }.map { ex(it, a) } + Cases.typical(rest) { r(it).change.toDouble() }.map { ex(it, b) }
    }

    private fun exPre(c: Ctx, all: List<ExerciseDetector.Response>): PatternCandidate? {
        val t = c.input.scaled(PRE_TREND)
        // Glucose 30 min before the start (k = -6) against at the start (k = 0).
        val rs = all.filter { !it.values[0].isNaN() && !it.values[-ExerciseDetector.FIRST_K].isNaN() }
        fun trend(r: ExerciseDetector.Response) = (r.values[-ExerciseDetector.FIRST_K] - r.values[0]).toDouble()
        val stable = rs.filter { abs(trend(it)) < t }
        val best = listOf(rs.filter { trend(it) <= -t } to "falling", rs.filter { trend(it) >= t } to "rising").mapNotNull { (g, name) ->
            if (g.size < MIN_GROUP || stable.size < MIN_GROUP) return@mapNotNull null
            val cm = cmp(g, stable) { it.change.toDouble() }
            if (solid(cm, c.input.scaled(PRE_DIFF), { it.event.startUtc }, { it.change.toDouble() })) cm to name else null
        }.maxByOrNull { it.first.z } ?: return null
        val (cm, name) = best
        val falling = name == "falling"
        val hitLabel = Msg(if (falling) "pat_label_gl_falling" else "pat_label_gl_rising"); val steadyLabel = Msg("pat_label_gl_steady")
        return card(
            c, "ctx.expre", "ctx-expre", msg(if (falling) "pat_ctx_expre_title_fall" else "pat_ctx_expre_title_rise"),
            msg(if (falling) "pat_ctx_expre_sum_fall" else "pat_ctx_expre_sum_rise", cm.hit.size, abs(cm.hit.map { it.change }.average()).roundToInt(), cm.rest.map { it.change }.average().roundToInt().let { if (it > 0) "+$it" else "$it" }, cm.rest.size),
            exGraph(cm.hit, cm.rest, hitLabel, steadyLabel), SampleUnit.ACTIVITIES, cm,
            msg(if (falling) "pat_ctx_lower_end" else "pat_ctx_higher_end", abs(cm.diff).roundToInt()), margin(cm, "mg/dL"),
            exLimits + msg("pat_ctx_expre_lim", t.roundToInt()),
            exCases(cm.hit, cm.rest, hitLabel, steadyLabel, { it }, 30) { it, x -> msg("pat_ctx_expre_case", Cases.signed(trend(it)), Cases.signed(x.change)) },
            if (falling) PatternTone.UNFAVORABLE else PatternTone.MIXED,
            rank(55.0, cm.z, cm.hit.size + cm.rest.size), msg("pat_ctx_expre_baseline"), TipTopic.CTX_EX_PRE
        )
    }

    private fun exGap(c: Ctx, all: List<ExerciseDetector.Response>): PatternCandidate? {
        val meals = c.input.sittings.filter { it.totalCarbs >= 15 }.sortedBy { it.endTime }
        class Item(val r: ExerciseDetector.Response, val gapMin: Double)
        val items = all.mapNotNull { r ->
            val last = meals.lastOrNull { it.endTime <= r.event.startUtc } ?: return@mapNotNull null
            val gap = (r.event.startUtc - last.endTime) / 60_000.0
            if (gap > 8 * 60) null else Item(r, gap)
        }
        if (items.size < 2 * MIN_GROUP) return null
        val cut = median(items.map { it.gapMin })
        val (soon, later) = items.partition { it.gapMin < cut }.let { (s, l) -> if (s.size >= MIN_GROUP) s to l else items.partition { it.gapMin <= cut } }
        if (soon.size < MIN_GROUP || later.size < MIN_GROUP) return null
        val soonTypical = median(soon.map { it.gapMin }); val laterTypical = median(later.map { it.gapMin })
        if (laterTypical - soonTypical < GAP_MIN_APART) return null
        val cm = cmp(soon, later) { it.r.change.toDouble() }
        if (!solid(cm, c.input.scaled(GAP_DIFF), { it.r.event.startUtc }, { it.r.change.toDouble() })) return null
        val lowerSoon = cm.diff < 0
        val low = if (lowerSoon) soon else later; val other = if (lowerSoon) later else soon
        fun h(m: Double) = if (m >= 90) "${(m / 60).roundToInt()} h" else "${m.roundToInt()} min"
        val lowLabel = Msg(if (lowerSoon) "pat_label_soon_after" else "pat_label_long_after"); val otherLabel = Msg(if (lowerSoon) "pat_label_longer_after" else "pat_label_sooner_after")
        return card(
            c, "ctx.exgap", "ctx-exgap", msg(if (lowerSoon) "pat_ctx_exgap_title_soon" else "pat_ctx_exgap_title_long"),
            msg(if (lowerSoon) "pat_ctx_exgap_sum_soon" else "pat_ctx_exgap_sum_long", h(cut), low.size, low.map { it.r.change }.average().roundToInt(), other.map { it.r.change }.average().roundToInt(), other.size),
            exGraph(low.map { it.r }, other.map { it.r }, lowLabel, otherLabel),
            SampleUnit.ACTIVITIES, Cmp(low, other, -abs(cm.diff), cm.se), msg("pat_ctx_lower_end", abs(cm.diff).roundToInt()), margin(cm, "mg/dL"),
            exLimits + msg("pat_ctx_exgap_lim", h(cut)),
            exCases(low, other, lowLabel, otherLabel, { it.r }) { it, x -> msg("pat_ctx_exgap_case", h(it.gapMin), Cases.signed(x.change)) },
            PatternTone.MIXED, rank(45.0, cm.z, items.size), msg("pat_ctx_exgap_baseline"), TipTopic.CTX_EX_GAP
        )
    }

    private fun exType(c: Ctx, all: List<ExerciseDetector.Response>): PatternCandidate? {
        val workouts = all.filter { it.event.isWorkout && !it.event.label.startsWith("@") }
        val best = workouts.groupBy { it.event.label }.mapNotNull { (label, g) ->
            val rest = workouts.filter { it.event.label != label }
            if (g.size < MIN_GROUP || rest.size < MIN_GROUP) return@mapNotNull null
            val cm = cmp(g, rest) { it.change.toDouble() }
            if (solid(cm, c.input.scaled(TYPE_DIFF), { it.event.startUtc }, { it.change.toDouble() })) cm to label else null
        }.maxByOrNull { it.first.z } ?: return null
        val (cm, label) = best
        val lower = cm.diff < 0; val l = label.lowercase()
        val labelMsg = msg("pat_literal", label); val otherLabel = Msg("pat_label_other_workouts")
        return card(
            c, "ctx.extype", "ctx-extype", msg(if (lower) "pat_ctx_extype_title_low" else "pat_ctx_extype_title_high", l),
            msg("pat_ctx_extype_summary", l, cm.hit.size, cm.hit.map { it.change }.average().roundToInt(), cm.rest.map { it.change }.average().roundToInt(), cm.rest.size),
            exGraph(cm.hit, cm.rest, labelMsg, otherLabel), SampleUnit.WORKOUTS, cm, msg(if (lower) "pat_ctx_lower" else "pat_ctx_higher", abs(cm.diff).roundToInt()),
            margin(cm, "mg/dL"),
            exLimits + msg("pat_ctx_extype_lim"),
            exCases(cm.hit, cm.rest, labelMsg, otherLabel, { it }), if (lower) PatternTone.UNFAVORABLE else PatternTone.MIXED,
            rank(40.0, cm.z, cm.hit.size + cm.rest.size), msg("pat_ctx_extype_baseline"), TipTopic.CTX_EX_TYPE
        )
    }

    // ---- meals --------------------------------------------------------------------------------------------------

    private fun mealGraph(hit: List<MealDetector.Response>, rest: List<MealDetector.Response>, a: Msg, b: Msg): MiniGraph =
        eventGraph(
            hit.map { EventCurve(it.values, it.baseline) }, rest.map { EventCurve(it.values, it.baseline) },
            MealDetector.FIRST_K, MealDetector.LAST_K, 3, a, b, Msg(EVENT_MEAL), null, listOf(0, 60, 120, 180, 240)
        )

    private val mealLimits = listOf(msg("pat_meal_lim_excluded"), msg("pat_ctx_meal_lim2"))

    /** Meals of similar size (carbohydrates within 35 % of the median meal), so size isn't what differs. */
    private fun carbBand(meals: List<MealDetector.Response>): List<MealDetector.Response> {
        val med = median(meals.map { it.carbs.toDouble() })
        return meals.filter { abs(it.carbs - med) <= SIMILAR_CARBS * med }
    }

    /** The two groups are alike in size and, where recorded, in fat + protein, so composition isn't what differs. */
    private fun comparable(a: List<MealDetector.Response>, b: List<MealDetector.Response>): Boolean {
        val ca = median(a.map { it.carbs.toDouble() }); val cb = median(b.map { it.carbs.toDouble() })
        if (maxOf(ca, cb) > CARBS_RATIO * minOf(ca, cb)) return false
        val fa = a.filter { it.hasComposition }.map { (it.fat + it.protein).toDouble() }
        val fb = b.filter { it.hasComposition }.map { (it.fat + it.protein).toDouble() }
        if (fa.size >= 3 && fb.size >= 3) {
            val ma = median(fa); val mb = median(fb)
            if (maxOf(ma, mb) > COMPOSITION_RATIO * minOf(ma, mb).coerceAtLeast(1.0)) return false
        }
        return true
    }

    /** Typical meals of both groups, shaded over the first 3 hours, with the peak marked. */
    private fun mealCases(hit: List<MealDetector.Response>, rest: List<MealDetector.Response>, a: Msg, b: Msg): List<PatternExample> {
        fun ex(r: MealDetector.Response, group: Msg) = r.case(0, 180, msg("pat_ctx_meal_case", r.peakRise.roundToInt(), r.peak.roundToInt()), r.peakK, group)
        return Cases.typical(hit) { it.peakRise.toDouble() }.map { ex(it, a) } + Cases.typical(rest) { it.peakRise.toDouble() }.map { ex(it, b) }
    }

    private fun secondRise(r: MealDetector.Response): Double? {
        fun v(k: Int) = r.values[k - MealDetector.FIRST_K]
        val trough = (12..30).map(::v).filter { !it.isNaN() }.minOrNull() ?: return null
        val late = (30..48).map(::v).filter { !it.isNaN() }.maxOrNull() ?: return null
        return (late - trough).coerceAtLeast(0f).toDouble()
    }

    private fun mealDelay(c: Ctx, all: List<MealDetector.Response>): PatternCandidate? {
        val band = carbBand(all.filter { it.hasComposition && secondRise(it) != null })
        if (band.size < 2 * MIN_GROUP) return null
        val cut = median(band.map { (it.fat + it.protein).toDouble() })
        if (cut < RICH_MIN_CUT_G) return null
        val (rich, light) = band.partition { it.fat + it.protein > cut }.let { (r, l) -> if (r.size >= MIN_GROUP) r to l else band.partition { it.fat + it.protein >= cut } }
        if (rich.size < MIN_GROUP || light.size < MIN_GROUP) return null
        if (median(rich.map { (it.fat + it.protein).toDouble() }) < RICH_RATIO * median(light.map { (it.fat + it.protein).toDouble() })) return null
        val cm = cmp(rich, light) { secondRise(it)!! }
        if (cm.diff <= 0 || !solid(cm, c.input.scaled(SECOND_RISE_DIFF), { it.startUtc }, { secondRise(it)!! })) return null
        val carbsMed = median(band.map { it.carbs.toDouble() }).roundToInt()
        return card(
            c, "ctx.mealdelay", "ctx-mealdelay", msg("pat_ctx_delay_title"),
            msg("pat_ctx_delay_summary", carbsMed, cut.roundToInt(), rich.size, cm.hit.map { secondRise(it)!! }.average().roundToInt(), cm.rest.map { secondRise(it)!! }.average().roundToInt(), light.size),
            mealGraph(rich, light, Msg("pat_label_richer"), Msg("pat_label_lighter_meals")), SampleUnit.MEALS, cm, msg("pat_ctx_delay_effect", cm.diff.roundToInt()), margin(cm, "mg/dL"),
            mealLimits + listOf(msg("pat_ctx_delay_lim3", (SIMILAR_CARBS * 100).roundToInt()), msg("pat_ctx_delay_lim4")),
            listOf(rich to Msg("pat_label_richer"), light to Msg("pat_label_lighter_meals")).flatMap { (g, label) ->
                Cases.typical(g) { secondRise(it)!! }.map { r ->
                    val peakK = (30..48).filter { !r.values[it - MealDetector.FIRST_K].isNaN() }.maxByOrNull { r.values[it - MealDetector.FIRST_K] }
                    r.case(150, 240, msg("pat_ctx_delay_case", Cases.signed(secondRise(r)!!), r.fat + r.protein), peakK, label)
                }
            },
            PatternTone.UNFAVORABLE,
            rank(50.0, cm.z, band.size), msg("pat_ctx_delay_baseline"), TipTopic.CTX_MEAL_DELAY
        )
    }

    private fun periodOf(hour: Int) = when (hour) { in 5 until 11 -> "morning"; in 11 until 16 -> "midday"; in 16 until 24 -> "evening"; else -> null }

    private fun mealTime(c: Ctx, all: List<MealDetector.Response>): PatternCandidate? {
        val band = carbBand(all).mapNotNull { r -> periodOf(Instant.ofEpochMilli(r.startUtc).atZone(c.zone).hour)?.let { it to r } }
        val best = band.map { it.first }.distinct().mapNotNull { p ->
            val g = band.filter { it.first == p }.map { it.second }; val rest = band.filter { it.first != p }.map { it.second }
            if (g.size < MIN_GROUP || rest.size < MIN_GROUP || !comparable(g, rest)) return@mapNotNull null
            val cm = cmp(g, rest) { it.peakRise.toDouble() }
            if (solid(cm, c.input.scaled(MEAL_DIFF), { it.startUtc }, { it.peakRise.toDouble() })) cm to p else null
        }.maxByOrNull { it.first.z } ?: return null
        val (cm, p) = best
        val worse = cm.diff > 0
        return card(
            c, "ctx.mealtime.$p", "ctx-mealtime", msg(if (worse) "pat_ctx_time_title_worse" else "pat_ctx_time_title_better", ref("pat_period_$p")),
            msg("pat_ctx_time_summary", ref("pat_period_$p"), cm.hit.size, cm.hit.map { it.peakRise }.average().roundToInt(), cm.rest.map { it.peakRise }.average().roundToInt(), cm.rest.size),
            mealGraph(cm.hit, cm.rest, Msg("pat_label_per_$p"), Msg("pat_label_meals_other_times")), SampleUnit.MEALS, cm,
            msg(if (worse) "pat_ctx_peak_up" else "pat_ctx_peak_down", abs(cm.diff).roundToInt()), margin(cm, "mg/dL"),
            mealLimits + listOf(msg("pat_ctx_time_lim3", (SIMILAR_CARBS * 100).roundToInt()), msg("pat_ctx_time_lim4")),
            mealCases(cm.hit, cm.rest, Msg("pat_label_per_$p"), Msg("pat_label_meals_other_times")), if (worse) PatternTone.UNFAVORABLE else PatternTone.FAVORABLE,
            rank(if (worse) 50.0 else 28.0, cm.z, cm.hit.size + cm.rest.size), msg("pat_ctx_time_baseline"),
            if (worse) TipTopic.CTX_MEAL_WORSE else TipTopic.CTX_MEAL_BETTER
        )
    }

    private fun isWeekend(ms: Long, zone: java.time.ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).dayOfWeek.let { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }

    private fun mealWeekend(c: Ctx, all: List<MealDetector.Response>): PatternCandidate? {
        val band = carbBand(all)
        val (weekend, weekday) = band.partition { isWeekend(it.startUtc, c.zone) }
        if (weekend.size < MIN_GROUP || weekday.size < MIN_GROUP || !comparable(weekend, weekday)) return null
        val cm = cmp(weekend, weekday) { it.peakRise.toDouble() }
        if (!solid(cm, c.input.scaled(MEAL_DIFF), { it.startUtc }, { it.peakRise.toDouble() })) return null
        val worse = cm.diff > 0
        return card(
            c, "ctx.mealweekend", "ctx-mealweekend", msg(if (worse) "pat_ctx_we_title_worse" else "pat_ctx_we_title_better"),
            msg("pat_ctx_we_summary", weekend.size, weekend.map { it.peakRise }.average().roundToInt(), weekday.map { it.peakRise }.average().roundToInt(), weekday.size),
            mealGraph(weekend, weekday, Msg("pat_label_weekend_meals"), Msg("pat_label_weekday_meals")), SampleUnit.MEALS, cm, msg(if (worse) "pat_ctx_peak_up" else "pat_ctx_peak_down", abs(cm.diff).roundToInt()),
            margin(cm, "mg/dL"),
            mealLimits + msg("pat_ctx_we_lim3"),
            mealCases(weekend, weekday, Msg("pat_label_weekend_meals"), Msg("pat_label_weekday_meals")), if (worse) PatternTone.UNFAVORABLE else PatternTone.FAVORABLE,
            rank(if (worse) 45.0 else 26.0, cm.z, weekend.size + weekday.size), msg("pat_ctx_we_baseline"),
            if (worse) TipTopic.CTX_MEAL_WORSE else TipTopic.CTX_MEAL_BETTER
        )
    }

    private fun mealActivity(c: Ctx, all: List<MealDetector.Response>): PatternCandidate? {
        val events = c.input.events.filter { it.durationMin >= MIN_ACTIVITY_MIN }
        val window = ACTIVITY_BEFORE_MEAL_H * 3_600_000L
        val band = carbBand(all)
        val (after, other) = band.partition { r -> events.any { it.startUtc < r.startUtc && it.endUtc > r.startUtc - window } }
        if (after.size < MIN_GROUP || other.size < MIN_GROUP || !comparable(after, other)) return null
        val cm = cmp(after, other) { it.peakRise.toDouble() }
        if (!solid(cm, c.input.scaled(MEAL_DIFF), { it.startUtc }, { it.peakRise.toDouble() })) return null
        val worse = cm.diff > 0
        return card(
            c, "ctx.mealactivity", "ctx-mealactivity", msg(if (worse) "pat_ctx_act_title_worse" else "pat_ctx_act_title_better"),
            msg("pat_ctx_act_summary", ACTIVITY_BEFORE_MEAL_H, after.size, after.map { it.peakRise }.average().roundToInt(), other.map { it.peakRise }.average().roundToInt(), other.size),
            mealGraph(after, other, Msg("pat_label_after_activity"), Msg("pat_label_no_activity_before")), SampleUnit.MEALS, cm, msg(if (worse) "pat_ctx_peak_up" else "pat_ctx_peak_down", abs(cm.diff).roundToInt()),
            margin(cm, "mg/dL"),
            mealLimits + msg("pat_ctx_act_lim3", MIN_ACTIVITY_MIN, ACTIVITY_BEFORE_MEAL_H),
            mealCases(after, other, Msg("pat_label_after_activity"), Msg("pat_label_no_activity_before")), if (worse) PatternTone.UNFAVORABLE else PatternTone.FAVORABLE,
            rank(if (worse) 45.0 else 27.0, cm.z, after.size + other.size), msg("pat_ctx_act_baseline"),
            if (worse) TipTopic.CTX_MEAL_WORSE else TipTopic.CTX_MEAL_BETTER
        )
    }

    // ---- previous-day activity -> the night -------------------------------------------------------------------------

    private class NightDay(val date: LocalDate, val minutes: Int, val tir: Double, val next: DayGlucose)

    private fun night(c: Ctx): PatternCandidate? {
        val input = c.input
        val zone = c.zone
        val byDate = input.days.associateBy { it.date }
        val minutesByDay = input.events.groupBy { Instant.ofEpochMilli(it.startUtc).atZone(zone).toLocalDate() }.mapValues { (_, e) -> e.sumOf { it.durationMin } }
        // Days with activity ending in the evening belong to the evening-activity pattern and are left out.
        val eveningDays = input.events.filter { Instant.ofEpochMilli(it.endUtc).atZone(zone).hour in 17 until 22 }
            .map { Instant.ofEpochMilli(it.endUtc).atZone(zone).toLocalDate() }.toSet()
        val first = input.today.minusDays((WINDOW_DAYS - 1).toLong())
        val t = input.thresholds
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { it < input.today.minusDays(1) }.mapNotNull { d ->
            if (d in eveningDays) return@mapNotNull null
            val next = byDate[d.plusDays(1)]?.takeIf { it.coverage(0 until NIGHT_END_BIN) >= MIN_COVERAGE } ?: return@mapNotNull null
            val v = (0 until NIGHT_END_BIN).map { next.bins[it] }.filter { !it.isNaN() }
            NightDay(d, minutesByDay[d] ?: 0, v.count { it >= t.low && it <= t.high }.toDouble() / v.size * 100, next)
        }.toList()
        val activeMinutes = minutesByDay.filterKeys { it !in eveningDays }.values.filter { it > 0 }.sorted()
        if (activeMinutes.size < MIN_GROUP) return null
        val cutMin = maxOf(MIN_ACTIVE_DAY_MIN, activeMinutes[activeMinutes.size / 2])
        val (active, quiet) = days.partition { it.minutes >= cutMin }
        if (active.size < MIN_GROUP || quiet.size < MIN_GROUP) return null
        val cm = cmp(active, quiet) { it.tir }
        if (!solid(cm, NIGHT_TIR_DIFF, { input.startOfDayUtc(it.date) }, { it.tir })) return null
        val better = cm.diff > 0
        val agpA = agpOfDays(active.map { it.next }) ?: return null
        val agpQ = agpOfDays(quiet.map { it.next }) ?: return null
        return card(
            c, "ctx.night", "ctx-night", msg(if (better) "pat_ctx_night_title_better" else "pat_ctx_night_title_worse"),
            msg("pat_ctx_night_summary", cutMin, active.size, pct(active.map { it.tir }.average()), pct(quiet.map { it.tir }.average()), quiet.size),
            MiniGraph.ProfileComparison(Msg("pat_label_night_after_active"), agpA.medianGraph(), Msg("pat_label_night_after_quiet"), agpQ.medianGraph()),
            SampleUnit.NIGHTS, cm, msg(if (better) "pat_ctx_night_eff_up" else "pat_ctx_night_eff_down", abs(cm.diff).roundToInt()), margin(cm, "points"),
            listOf(msg("pat_ctx_night_lim1"), msg("pat_ctx_night_lim2"), msg("pat_ctx_night_lim3")),
            listOf(active to Msg("pat_label_night_after_active"), quiet to Msg("pat_label_night_after_quiet")).flatMap { (g, label) ->
                Cases.typical(g) { it.tir }.map { n ->
                    val day0 = input.startOfDayUtc(n.date)
                    val h = 3_600_000L
                    PatternExample(
                        fromUtc = day0 + 12 * h, toUtc = day0 + 31 * h, focusFromUtc = day0 + 24 * h, focusToUtc = day0 + 30 * h,
                        note = msg("pat_ctx_night_case", n.minutes, n.tir.roundToInt()), group = label
                    )
                }
            },
            if (better) PatternTone.FAVORABLE else PatternTone.UNFAVORABLE, rank(if (better) 28.0 else 40.0, cm.z, active.size + quiet.size),
            msg("pat_ctx_night_baseline"), if (better) TipTopic.CTX_NIGHT_BETTER else TipTopic.CTX_NIGHT_WORSE
        )
    }

    // ---- sleep x previous-day activity -----------------------------------------------------------------------------

    private fun dailyMinutes(c: Ctx): Map<LocalDate, Int> =
        c.input.events.groupBy { Instant.ofEpochMilli(it.startUtc).atZone(c.zone).toLocalDate() }.mapValues { (_, e) -> e.sumOf { it.durationMin } }

    private fun activeCut(minutes: Map<LocalDate, Int>): Int? {
        val nonZero = minutes.values.filter { it > 0 }.sorted()
        return if (nonZero.size < MIN_GROUP) null else maxOf(MIN_ACTIVE_DAY_MIN, nonZero[nonZero.size / 2])
    }

    private fun sleepGraph(hit: List<SleepDetector.Night>, rest: List<SleepDetector.Night>, a: Msg, b: Msg): MiniGraph? {
        val agpA = agpOfDays(hit.map { it.day }) ?: return null
        val agpB = agpOfDays(rest.map { it.day }) ?: return null
        return MiniGraph.ProfileComparison(a, agpA.medianGraph(), b, agpB.medianGraph())
    }

    /**
     * Does the short-sleep penalty depend on how active the day before was? Four groups (short / longer night x active /
     * quieter previous day), each >= 6 nights; the difference between the two penalties must itself be 3x its standard
     * error. Differs from `sleep.short`, which pools all nights and never looks at the previous day.
     */
    private fun sleepActivity(c: Ctx): PatternCandidate? {
        val nights = SleepDetector.nights(c.input, WINDOW_DAYS)
        val minutes = dailyMinutes(c)
        val cutMin = activeCut(minutes) ?: return null
        if (nights.size < 4 * MIN_GROUP) return null
        val sleepCut = median(nights.map { it.n.asleepMin.toDouble() })
        val (short, long) = nights.partition { it.n.asleepMin < sleepCut }.let { (s, l) -> if (s.size >= 2 * MIN_GROUP) s to l else nights.partition { it.n.asleepMin <= sleepCut } }
        if (median(long.map { it.n.asleepMin.toDouble() }) - median(short.map { it.n.asleepMin.toDouble() }) < SLEEP_GAP_MIN) return null
        fun active(n: SleepDetector.Night) = (minutes[n.n.wakeDate.minusDays(1)] ?: 0) >= cutMin
        val sA = short.filter(::active); val sQ = short.filter { !active(it) }; val lA = long.filter(::active); val lQ = long.filter { !active(it) }
        if (listOf(sA, sQ, lA, lQ).any { it.size < MIN_GROUP }) return null
        val penA = cmp(lA, sA) { it.tir }; val penQ = cmp(lQ, sQ) { it.tir }
        val inter = penQ.diff - penA.diff
        val seI = sqrt(penQ.se * penQ.se + penA.se * penA.se)
        if (abs(inter) < SLEEP_TIR_DIFF || abs(inter) < INTERACTION_SE * seI) return null
        val quietWorse = inter > 0
        val big = if (quietWorse) penQ else penA
        val hit = if (quietWorse) sQ else sA
        // The larger penalty must itself be real and hold in both halves of the period.
        if (!solid(Cmp(big.hit, big.rest, big.diff, big.se), SLEEP_TIR_DIFF, { it.n.endUtc }, { it.tir })) return null
        val small = if (quietWorse) penA else penQ
        val whichS = ref(if (quietWorse) "pat_day_quieter_s" else "pat_day_active_s")
        val whichP = ref(if (quietWorse) "pat_day_quieter_p" else "pat_day_active_p")
        val otherP = ref(if (quietWorse) "pat_day_active_p" else "pat_day_quieter_p")
        val shortLabel = msg("pat_label_short_after", whichS); val longerLabel = msg("pat_label_longer_after_day", whichS)
        val graph = sleepGraph(hit, big.hit, shortLabel, longerLabel) ?: return null
        return card(
            c, "ctx.sleepactivity", "ctx-sleepactivity", Msg(if (quietWorse) "pat_ctx_sa_title_quiet" else "pat_ctx_sa_title_active"),
            msg("pat_ctx_sa_summary", whichS, "${(sleepCut / 60).toInt()} h ${"%02d".format(sleepCut.toInt() % 60)} min", hit.size, pct(hit.map { it.tir }.average()), pct(big.hit.map { it.tir }.average()), big.hit.size, otherP, abs(small.diff).roundToInt()),
            graph, SampleUnit.NIGHTS, Cmp(hit, big.hit, -big.diff, big.se), msg("pat_ctx_sa_effect", big.diff.roundToInt(), small.diff.roundToInt()),
            msg("pat_ctx_sa_unc", abs(inter).roundToInt(), "%.1f".format(java.util.Locale.ROOT, abs(inter) / seI.coerceAtLeast(0.01)), seI.roundToInt()),
            listOf(msg("pat_ctx_sa_lim1", MIN_GROUP, WINDOW_DAYS), msg("pat_ctx_sa_lim2", cutMin), msg("pat_ctx_sa_lim3")),
            Cases.typical(hit) { it.tir }.map { it.case(c.input, "pat_ctx_sleep_case_active", (minutes[it.n.wakeDate.minusDays(1)] ?: 0).toString(), shortLabel) } +
                Cases.typical(big.hit) { it.tir }.map { it.case(c.input, "pat_ctx_sleep_case_active", (minutes[it.n.wakeDate.minusDays(1)] ?: 0).toString(), longerLabel) },
            PatternTone.UNFAVORABLE, rank(42.0, abs(inter) / seI, nights.size), msg("pat_ctx_sa_baseline", whichP), TipTopic.CTX_SLEEP_ACTIVITY
        )
    }

    // ---- good sleep context -----------------------------------------------------------------------------------------

    /**
     * The favourable counterpart: nights that were long enough, started near the usual bedtime and followed an active
     * day, against all other nights, judged on the rest of the wake-up day. Several conditions at once, so >= 8 nights in
     * the group and 3x the margin of error.
     */
    private fun sleepGood(c: Ctx): PatternCandidate? {
        val nights = SleepDetector.nights(c.input, WINDOW_DAYS)
        val cutMin = activeCut(dailyMinutes(c)) ?: return null
        if (nights.size < GOOD_NIGHTS + MIN_GROUP) return null
        val minutes = dailyMinutes(c)
        val sleepCut = median(nights.map { it.n.asleepMin.toDouble() }); val bedMed = median(nights.map { it.n.bedtimeMin.toDouble() })
        val (good, rest) = nights.partition {
            it.n.asleepMin >= sleepCut && abs(it.n.bedtimeMin - bedMed) < REGULAR_BED_MIN && (minutes[it.n.wakeDate.minusDays(1)] ?: 0) >= cutMin
        }
        if (good.size < GOOD_NIGHTS || rest.size < MIN_GROUP) return null
        val cm = cmp(good, rest) { it.tir }
        if (cm.diff <= 0 || cm.diff < SLEEP_TIR_DIFF || cm.diff < INTERACTION_SE * cm.se || !solid(cm, SLEEP_TIR_DIFF, { it.n.endUtc }, { it.tir })) return null
        val graph = sleepGraph(good, rest, Msg("pat_label_full_regular"), Msg("pat_label_other_nights")) ?: return null
        fun clock(m: Double) = (((m.roundToInt() + 18 * 60) % 1440).let { "%02d:%02d".format(it / 60, it % 60) })
        return card(
            c, "ctx.sleepgood", "ctx-sleepgood", msg("pat_ctx_sg_title"),
            msg("pat_ctx_sg_summary", "${(sleepCut / 60).toInt()} h ${"%02d".format(sleepCut.toInt() % 60)} min", clock(bedMed), good.size, pct(good.map { it.tir }.average()), pct(rest.map { it.tir }.average()), rest.size),
            graph, SampleUnit.NIGHTS, cm, msg("pat_ex_nextday_effect", cm.diff.roundToInt()), margin(cm, "points"),
            listOf(msg("pat_ctx_sg_lim1", GOOD_NIGHTS), msg("pat_ctx_sg_lim2", cutMin), msg("pat_ctx_sg_lim3")),
            Cases.typical(good) { it.tir }.map { it.case(c.input, "pat_ctx_sleep_case_bed", Cases.clock(it.n.startUtc, c.zone), Msg("pat_label_full_regular")) } +
                Cases.typical(rest) { it.tir }.map { it.case(c.input, "pat_ctx_sleep_case_bed", Cases.clock(it.n.startUtc, c.zone), Msg("pat_label_other_nights")) },
            PatternTone.FAVORABLE, rank(30.0, cm.z, nights.size), msg("pat_ctx_sg_baseline"), TipTopic.CTX_SLEEP_GOOD
        )
    }

    // ---- late dinner x composition ------------------------------------------------------------------------------------

    /**
     * Within late dinners, does recorded composition matter? Rich (>= median fat + protein of late dinners) against
     * lighter late dinners of similar carbohydrates, on glucose between midnight and 06:00. Differs from
     * `combo.latedinner`, which compares late with early dinners; it replaces that card when it fires.
     */
    private fun lateDinnerRich(c: Ctx): PatternCandidate? {
        val dinners = CombinedDetector.dinnersOf(c.input, WINDOW_DAYS)
        if (dinners.size < 3 * MIN_GROUP) return null
        val cut = median(dinners.map { it.minute })
        val late = dinners.partition { it.minute > cut }.let { (l, _) -> if (l.size >= 2 * MIN_GROUP) l else dinners.filter { it.minute >= cut } }
        val early = dinners - late.toSet()
        if (early.isEmpty() || median(late.map { it.minute }) - median(early.map { it.minute }) < LATE_GAP_MIN) return null
        val comp = late.filter { it.fatProtein != null }
        if (comp.size < 2 * MIN_GROUP) return null
        val fpCut = median(comp.map { it.fatProtein!!.toDouble() })
        if (fpCut < RICH_MIN_CUT_G) return null
        val (rich, light) = comp.partition { it.fatProtein!! > fpCut }.let { (r, l) -> if (r.size >= MIN_GROUP) r to l else comp.partition { it.fatProtein!! >= fpCut } }
        if (rich.size < MIN_GROUP || light.size < MIN_GROUP) return null
        if (median(rich.map { it.fatProtein!!.toDouble() }) < RICH_RATIO * median(light.map { it.fatProtein!!.toDouble() })) return null
        val cr = median(rich.map { it.carbs.toDouble() }); val cl = median(light.map { it.carbs.toDouble() })
        if (maxOf(cr, cl) > CARBS_RATIO * minOf(cr, cl)) return null
        val cm = cmp(rich, light) { it.night }
        if (cm.diff <= 0 || cm.diff < INTERACTION_SE * cm.se || !solid(cm, c.input.scaled(LATE_RICH_DIFF), { it.startUtc }, { it.night })) return null
        fun curves(l: List<CombinedDetector.Dinner>) = l.map { EventCurve(it.values, it.baseline) }
        val graph = eventGraph(curves(rich), curves(light), -6, 120, 6, Msg("pat_label_late_rich"), Msg("pat_label_late_lighter"), Msg("pat_ev_dinner"), null, listOf(0, 120, 240, 360, 480, 600))
        fun hhmm(m: Double) = "%02d:%02d".format(m.roundToInt() / 60, m.roundToInt() % 60)
        return card(
            c, "ctx.latedinnerrich", "ctx-latedinnerrich", msg("pat_ctx_ld_title"),
            msg("pat_ctx_ld_summary", hhmm(median(late.map { it.minute })), fpCut.roundToInt(), rich.size, cm.diff.roundToInt(), light.size),
            graph, SampleUnit.DINNERS, cm, msg("pat_combo_dinner_effect", cm.diff.roundToInt()), margin(cm, "mg/dL"),
            listOf(msg("pat_ctx_ld_lim1", (CARBS_RATIO * 100 - 100).roundToInt()), msg("pat_ctx_ld_lim2", WINDOW_DAYS), msg("pat_ctx_ld_lim3")),
            Cases.typical(rich) { it.night }.map { it.case(c.input, msg("pat_combo_dinner_case", hhmm(it.minute), it.night.roundToInt()), Msg("pat_label_late_rich")) } +
                Cases.typical(light) { it.night }.map { it.case(c.input, msg("pat_combo_dinner_case", hhmm(it.minute), it.night.roundToInt()), Msg("pat_label_late_lighter")) },
            PatternTone.UNFAVORABLE, rank(52.0, cm.z, rich.size + light.size), msg("pat_ctx_ld_baseline"), TipTopic.CTX_LATE_RICH
        )
    }
}
