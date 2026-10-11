package uk.scimone.diafit.patterns.domain

import uk.scimone.diafit.core.domain.model.MealSitting
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
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
    const val WINDOW_DAYS = 28

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

    fun detect(input: DetectionInput): List<PatternCandidate> {
        val c = Ctx(input)
        val meals = MealDetector.responses(input)
        val ex = ExerciseDetector.responses(input)
        return listOfNotNull(
            exPre(c, ex), exGap(c, ex), exType(c, ex),
            mealDelay(c, meals), mealTime(c, meals), mealWeekend(c, meals), mealActivity(c, meals),
            night(c)
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
        "The difference is about ${"%.1f".format(c.z)}× its likely margin of error (±${c.se.roundToInt()} $unit), and it points the same way in the earlier and the later half of the period."

    private fun dateLabel(ms: Long, zone: java.time.ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).let {
        "${it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
    }

    private fun rank(base: Double, z: Double, n: Int) = base * (0.6 + 0.4 * (z / 6.0).coerceIn(0.0, 1.0)) * (0.7 + 0.3 * (n / 24.0).coerceAtMost(1.0))

    private fun card(
        c: Ctx, id: String, detector: String, title: String, summary: String, graph: MiniGraph, unit: String, cmp: Cmp<*>, effectLabel: String,
        uncertainty: String, limits: List<String>, examples: List<String>, tone: PatternTone, priority: Double, baseline: String,
        topic: TipTopic, escalate: Boolean = false
    ) = PatternCandidate(
        PatternCard(
            id = id, category = PatternCategory.COMBINED, detector = detector, title = title, summary = summary, miniGraph = graph,
            observationFromUtc = c.from, observationToUtc = c.input.nowUtc, baselineFromUtc = c.from, baselineToUtc = c.input.nowUtc, baselineLabel = baseline,
            sampleSize = cmp.hit.size + cmp.rest.size, sampleUnit = unit, positiveCount = cmp.hit.size, effectSize = abs(cmp.diff), effectLabel = effectLabel,
            uncertainty = uncertainty, limitations = limits, examples = examples, tone = tone, priority = priority
        ),
        topic, escalate
    )

    // ---- exercise ---------------------------------------------------------------------------------------------------

    private fun exGraph(hit: List<ExerciseDetector.Response>, rest: List<ExerciseDetector.Response>, a: String, b: String): MiniGraph =
        eventGraph(
            hit.map { EventCurve(it.values, it.baseline) }, rest.map { EventCurve(it.values, it.baseline) },
            ExerciseDetector.FIRST_K, ExerciseDetector.LAST_K, 3, a, b, "start", median(hit.map { it.event.durationMin.toDouble() }).roundToInt(),
            listOf(0, 120, 240, 360, 480)
        )

    private val exLimits = listOf(
        "Activities with a sizeable meal right around them, another activity within 2 hours, too little sensor data, or glucose already low at the start are left out.",
        "Insulin can't be separated from the effect of the activity here, and it is not used to explain the result."
    )

    private fun exExamples(g: List<ExerciseDetector.Response>, zone: java.time.ZoneId, lowest: Boolean) =
        (if (lowest) g.sortedBy { it.change } else g.sortedByDescending { it.change }).take(3).map {
            "${dateLabel(it.event.startUtc, zone)} · ${it.event.label.lowercase()} (${it.event.durationMin} min) · ${if (it.change > 0) "+" else ""}${it.change.roundToInt()} mg/dL"
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
        val dirWord = if (falling) "dropped" else "rose"
        return card(
            c, "ctx.expre", "ctx-expre", if (falling) "Bigger drop when glucose is already falling" else "Bigger rise when glucose is already rising",
            "When glucose was $name in the 30 minutes before the start (${cm.hit.size} activities), it ${dirWord} ${abs(cm.hit.map { it.change }.average()).roundToInt()} mg/dL by the end, against ${cm.rest.map { it.change }.average().roundToInt().let { if (it > 0) "+$it" else "$it" }} mg/dL when it was steady (${cm.rest.size}).",
            exGraph(cm.hit, cm.rest, "Glucose $name at the start", "Glucose steady at the start"), "activities", cm,
            "${abs(cm.diff).roundToInt()} mg/dL ${if (falling) "lower" else "higher"} at the end", margin(cm, "mg/dL"),
            exLimits + "Falling or rising means a change of at least ${t.roundToInt()} mg/dL over the 30 minutes before the start; steady is anything smaller.",
            exExamples(cm.hit, c.zone, falling), if (falling) PatternTone.UNFAVORABLE else PatternTone.MIXED,
            rank(55.0, cm.z, cm.hit.size + cm.rest.size), "Activities starting with steady glucose", TipTopic.CTX_EX_PRE
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
        val lowName = if (lowerSoon) "soon after eating" else "long after eating"
        fun h(m: Double) = if (m >= 90) "${(m / 60).roundToInt()} h" else "${m.roundToInt()} min"
        return card(
            c, "ctx.exgap", "ctx-exgap", "Lower glucose when active $lowName",
            "Activity ${if (lowerSoon) "within about ${h(cut)} of" else "more than about ${h(cut)} after"} a meal (${low.size}) ended with glucose ${low.map { it.r.change }.average().roundToInt()} mg/dL from its start, against ${other.map { it.r.change }.average().roundToInt()} mg/dL when ${if (lowerSoon) "longer" else "sooner"} after eating (${other.size}).",
            exGraph(low.map { it.r }, other.map { it.r }, if (lowerSoon) "Soon after eating" else "Long after eating", if (lowerSoon) "Longer after eating" else "Sooner after eating"),
            "activities", Cmp(low, other, -abs(cm.diff), cm.se), "${abs(cm.diff).roundToInt()} mg/dL lower at the end", margin(cm, "mg/dL"),
            exLimits + "Time since eating is the time since the last meal of at least 15 g carbs; your median is ${h(cut)}. Only meals in the 8 hours before count.",
            exExamples(low.map { it.r }, c.zone, true), PatternTone.MIXED, rank(45.0, cm.z, items.size), "Activities at a different time after eating", TipTopic.CTX_EX_GAP
        )
    }

    private val genericLabels = setOf("workout", "activity")

    private fun exType(c: Ctx, all: List<ExerciseDetector.Response>): PatternCandidate? {
        val workouts = all.filter { it.event.isWorkout && it.event.label.lowercase() !in genericLabels }
        val best = workouts.groupBy { it.event.label }.mapNotNull { (label, g) ->
            val rest = workouts.filter { it.event.label != label }
            if (g.size < MIN_GROUP || rest.size < MIN_GROUP) return@mapNotNull null
            val cm = cmp(g, rest) { it.change.toDouble() }
            if (solid(cm, c.input.scaled(TYPE_DIFF), { it.event.startUtc }, { it.change.toDouble() })) cm to label else null
        }.maxByOrNull { it.first.z } ?: return null
        val (cm, label) = best
        val lower = cm.diff < 0; val l = label.lowercase()
        return card(
            c, "ctx.extype", "ctx-extype", if (lower) "Bigger glucose drop in $l" else "Higher glucose after $l",
            "By the end of $l sessions (${cm.hit.size}) glucose had changed ${cm.hit.map { it.change }.average().roundToInt()} mg/dL, against ${cm.rest.map { it.change }.average().roundToInt()} mg/dL in your other logged workouts (${cm.rest.size}).",
            exGraph(cm.hit, cm.rest, label, "Other workouts"), "workouts", cm, "${abs(cm.diff).roundToInt()} mg/dL ${if (lower) "lower" else "higher"}",
            margin(cm, "mg/dL"),
            exLimits + "Only logged workouts with a name are compared; detected stretches of activity and unnamed workouts are not. Types differ in length and intensity too.",
            exExamples(cm.hit, c.zone, lower), if (lower) PatternTone.UNFAVORABLE else PatternTone.MIXED,
            rank(40.0, cm.z, cm.hit.size + cm.rest.size), "Your other logged workouts", TipTopic.CTX_EX_TYPE
        )
    }

    // ---- meals --------------------------------------------------------------------------------------------------

    private fun mealGraph(hit: List<MealDetector.Response>, rest: List<MealDetector.Response>, a: String, b: String): MiniGraph =
        eventGraph(
            hit.map { EventCurve(it.values, it.baseline) }, rest.map { EventCurve(it.values, it.baseline) },
            MealDetector.FIRST_K, MealDetector.LAST_K, 3, a, b, "meal", null, listOf(0, 60, 120, 180, 240)
        )

    private val mealLimits = listOf(
        "Meals with another meal close by, too little sensor data, or glucose already low at the start are left out.",
        "Insulin can't be separated from the meal's effect here, and it is not used to explain the result."
    )

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

    private fun mealExamples(g: List<MealDetector.Response>, zone: java.time.ZoneId, worst: Boolean) =
        (if (worst) g.sortedByDescending { it.peakRise } else g.sortedBy { it.peakRise }).take(3).map {
            "${dateLabel(it.startUtc, zone)} · ${it.type.type.lowercase()} · ${it.carbs} g · +${it.peakRise.roundToInt()} mg/dL"
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
            c, "ctx.mealdelay", "ctx-mealdelay", "Richer meals bring a second rise",
            "After similar-sized meals (about $carbsMed g carbs) with ${cut.roundToInt()} g or more of recorded fat and protein (${rich.size}), glucose rose again ${cm.hit.map { secondRise(it)!! }.average().roundToInt()} mg/dL between 2.5 and 4 hours after eating, against ${cm.rest.map { secondRise(it)!! }.average().roundToInt()} mg/dL after lighter ones (${light.size}).",
            mealGraph(rich, light, "Richer meals", "Lighter meals"), "meals", cm, "+${cm.diff.roundToInt()} mg/dL second rise", margin(cm, "mg/dL"),
            mealLimits + listOf(
                "Only meals with recorded fat or protein, within about ${(SIMILAR_CARBS * 100).roundToInt()} % of your median carbs, are compared; the logged absorption is not used.",
                "A second rise is the highest value 2.5–4 hours after eating minus the lowest value between 1 and 2.5 hours."
            ),
            mealExamples(rich.sortedByDescending { secondRise(it) }, c.zone, true), PatternTone.UNFAVORABLE,
            rank(50.0, cm.z, band.size), "Lighter meals of similar size", TipTopic.CTX_MEAL_DELAY
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
            c, "ctx.mealtime.$p", "ctx-mealtime", if (worse) "Bigger rises after $p meals" else "Smoother glucose after $p meals",
            "After $p meals (${cm.hit.size}) glucose rose ${cm.hit.map { it.peakRise }.average().roundToInt()} mg/dL, against ${cm.rest.map { it.peakRise }.average().roundToInt()} mg/dL after similar-sized meals at other times of day (${cm.rest.size}).",
            mealGraph(cm.hit, cm.rest, "${p.replaceFirstChar { it.uppercase() }} meals", "Meals at other times"), "meals", cm,
            "${if (worse) "+" else "−"}${abs(cm.diff).roundToInt()} mg/dL peak rise", margin(cm, "mg/dL"),
            mealLimits + listOf(
                "Meals are matched on size (within ${(SIMILAR_CARBS * 100).roundToInt()} % of your median carbs) and, where recorded, on fat and protein; what exactly you ate can still differ.",
                "Morning is 05:00–11:00, midday 11:00–16:00 and evening 16:00–24:00 by the start of the meal."
            ),
            mealExamples(cm.hit, c.zone, worse), if (worse) PatternTone.UNFAVORABLE else PatternTone.FAVORABLE,
            rank(if (worse) 50.0 else 28.0, cm.z, cm.hit.size + cm.rest.size), "Similar-sized meals at other times of day",
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
            c, "ctx.mealweekend", "ctx-mealweekend", if (worse) "Bigger rises after weekend meals" else "Smoother glucose after weekend meals",
            "After similar-sized meals at weekends (${weekend.size}) glucose rose ${weekend.map { it.peakRise }.average().roundToInt()} mg/dL, against ${weekday.map { it.peakRise }.average().roundToInt()} mg/dL on weekdays (${weekday.size}).",
            mealGraph(weekend, weekday, "Weekend meals", "Weekday meals"), "meals", cm, "${if (worse) "+" else "−"}${abs(cm.diff).roundToInt()} mg/dL peak rise",
            margin(cm, "mg/dL"),
            mealLimits + "Weekends differ from weekdays in many ways (meal timing, activity, sleep); the cause can't be told from this, and no work schedule or holiday is assumed.",
            mealExamples(weekend, c.zone, worse), if (worse) PatternTone.UNFAVORABLE else PatternTone.FAVORABLE,
            rank(if (worse) 45.0 else 26.0, cm.z, weekend.size + weekday.size), "Similar-sized weekday meals",
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
            c, "ctx.mealactivity", "ctx-mealactivity", if (worse) "Bigger rises after meals following activity" else "Smaller rises after meals following activity",
            "When a meal came within $ACTIVITY_BEFORE_MEAL_H hours of activity (${after.size}) glucose rose ${after.map { it.peakRise }.average().roundToInt()} mg/dL, against ${other.map { it.peakRise }.average().roundToInt()} mg/dL for similar-sized meals without activity beforehand (${other.size}).",
            mealGraph(after, other, "After activity", "No activity before"), "meals", cm, "${if (worse) "+" else "−"}${abs(cm.diff).roundToInt()} mg/dL peak rise",
            margin(cm, "mg/dL"),
            mealLimits + "Activity means a logged workout or a stretch of raised heart rate or steps of at least $MIN_ACTIVITY_MIN minutes that ended less than $ACTIVITY_BEFORE_MEAL_H hours before the meal, or was still going.",
            mealExamples(after, c.zone, worse), if (worse) PatternTone.UNFAVORABLE else PatternTone.FAVORABLE,
            rank(if (worse) 45.0 else 27.0, cm.z, after.size + other.size), "Similar-sized meals without activity beforehand",
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
            c, "ctx.night", "ctx-night", if (better) "Steadier nights after active days" else "Less steady nights after active days",
            "Between midnight and 06:00 after days with $cutMin+ minutes of activity (${active.size}) you spent ${pct(active.map { it.tir }.average())} of the time in range, against ${pct(quiet.map { it.tir }.average())} after quieter days (${quiet.size}).",
            MiniGraph.ProfileComparison("Night after active days", agpA.medianGraph(), "Night after quieter days", agpQ.medianGraph()),
            "nights", cm, "${if (better) "+" else "−"}${abs(cm.diff).roundToInt()} points in range overnight", margin(cm, "points"),
            listOf(
                "Days with activity ending between 17:00 and 22:00 are left out (they belong to the evening-activity pattern), as are nights with under 70 % sensor data.",
                "Days without activity data (for example a watch left at home) count as quieter days.",
                "Meals, sleep and routine differ between days too, so this can't show that activity is the reason."
            ),
            active.sortedBy { it.tir }.let { if (better) it.takeLast(3).reversed() else it.take(3) }.map { "${dateLabel(input.startOfDayUtc(it.date), zone)} · ${it.minutes} min active · ${it.tir.roundToInt()} % in range overnight" },
            if (better) PatternTone.FAVORABLE else PatternTone.UNFAVORABLE, rank(if (better) 28.0 else 40.0, cm.z, active.size + quiet.size),
            "Nights after quieter days", if (better) TipTopic.CTX_NIGHT_BETTER else TipTopic.CTX_NIGHT_WORSE
        )
    }
}
