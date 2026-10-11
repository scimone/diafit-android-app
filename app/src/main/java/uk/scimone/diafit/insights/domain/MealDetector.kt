package uk.scimone.diafit.insights.domain

import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealType
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Category B: recurring glucose responses around meals. Every meal (sitting) is aligned on its start and measured
 * from 30 min before to 4 h after. Meals are left out when the response can't be read cleanly: another meal starts
 * inside the window or shortly before it, the sensor covered under 70 % of it, the meal is tiny, or glucose was
 * already low when it began (a low treatment). Insulin activity can't be separated out, only described as context.
 * Composition findings prefer recorded fat and protein; the absorption the user logged is only a fallback and is
 * labelled as such.
 */
object MealDetector {
    const val WINDOW_DAYS = 28

    private const val MIN_CARBS = 15
    private const val MIN_COVERAGE = 0.7
    private const val MIN_MEALS = 8
    private const val MIN_MEALS_PER_TYPE = 6
    private const val MIN_GROUP = 5

    /** The response grid: 5-minute steps from [FIRST_K] (-30 min) to [LAST_K] (+240 min). */
    private const val FIRST_K = -6
    private const val LAST_K = 48
    private const val GRAPH_STEP = 3

    private const val MIN_LOW_SHARE = 0.20
    private const val MIN_LOW_MEALS = 3
    private const val SPIKE_MEDIAN_RISE = 60.0
    private const val SPIKE_RISE = 50.0
    private const val SPIKE_MIN_SHARE = 0.5
    private const val SLOW_MIN_SHARE = 0.30
    private const val GOOD_MIN_RISE = 15.0
    private const val GOOD_MAX_RISE = 50.0
    private const val GOOD_MIN_SHARE = 0.7
    private const val RICH_THRESHOLD_G = 35
    private const val RICH_MIN_DIFF = 20.0
    private const val TYPE_MIN_DIFF = 30.0
    private const val BOLUS_MIN_DIFF = 25.0
    private const val SE_FACTOR = 2.0
    private const val EARLY_BOLUS_MIN = 10
    private const val SIMILAR_CARBS = 0.35

    /** One meal's measured response. [values] index = k - FIRST_K for k in FIRST_K..LAST_K (5-min steps from the meal). */
    class Response(
        val startUtc: Long, val type: MealType, val carbs: Int, val protein: Int, val fat: Int,
        val hasComposition: Boolean, val impact: ImpactType, val values: FloatArray, val baseline: Float,
        val bolusLeadMin: Int?
    ) {
        private fun v(k: Int) = values[k - FIRST_K]
        private fun range(from: Int, to: Int) = (from..to).map(::v).filter { !it.isNaN() }
        val peak: Float = range(0, 36).maxOrNull() ?: Float.NaN
        val peakRise: Float get() = peak - baseline
        val peakK: Int = (0..36).firstOrNull { !v(it).isNaN() && v(it) == peak } ?: 0
        val lateMean: Float = range(36, 48).let { if (it.size >= 6) it.average().toFloat() else Float.NaN }
        val lateRise: Float get() = lateMean - baseline
        fun lowBins(low: Int) = range(12, 48).count { it < low }
        fun minAfter(): Pair<Int, Float>? = (12..48).filter { !v(it).isNaN() }.minByOrNull { v(it) }?.let { it to v(it) }
    }

    fun detect(input: DetectionInput): List<InsightCandidate> {
        val responses = responses(input)
        if (responses.size < MIN_MEALS) return emptyList()
        val c = Ctx(input, responses)
        val out = ArrayList<InsightCandidate>()
        out += scoped(c, ::lows)
        out += scoped(c, ::spikes)
        out += scoped(c, ::slow)
        out += scoped(c, ::good).sortedByDescending { it.card.priority }.take(2)
        out += rich(c)
        val spikeTypes = out.filter { it.card.detector == "meal-spike" }.map { it.card.id.substringAfterLast('.') }.toSet()
        out += typeDifferences(c).filter { it.card.id.substringAfterLast('.') !in spikeTypes }
        out += bolusTiming(c)
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
        val sittings = input.sittings.sortedBy { it.startTime }
        val out = ArrayList<Response>()
        for ((i, s) in sittings.withIndex()) {
            val t0 = s.startTime
            // Only meals whose whole window is in the past and in the period.
            if (t0 < since || t0 + (LAST_K * BIN_MINUTES) * 60_000L > input.nowUtc || s.totalCarbs < MIN_CARBS) continue
            val next = sittings.getOrNull(i + 1)
            if (next != null && next.startTime - t0 <= LAST_K * BIN_MINUTES * 60_000L) continue
            val prev = sittings.getOrNull(i - 1)
            if (prev != null && t0 - prev.startTime <= 120 * 60_000L) continue
            val values = FloatArray(LAST_K - FIRST_K + 1) { lookup.at(t0 + (it + FIRST_K) * BIN_MINUTES * 60_000L) }
            val after = (0..LAST_K).count { !values[it - FIRST_K].isNaN() }
            if (after.toDouble() / (LAST_K + 1) < MIN_COVERAGE) continue
            val base = (-3..0).map { values[it - FIRST_K] }.filter { !it.isNaN() }
            if (base.isEmpty()) continue
            val baseline = base.average().toFloat()
            if (baseline < low) continue // glucose was already low: this meal was probably a low treatment
            val protein = s.courses.sumOf { it.proteins ?: 0 }
            val fat = s.courses.sumOf { it.fats ?: 0 }
            // A bolus (not an SMB) within 45 min before to 30 min after the start: the largest one is the meal's.
            val bolus = input.boluses.filter { !it.isSmb && it.timestampUtc in (t0 - 45 * 60_000L)..(t0 + 30 * 60_000L) }.maxByOrNull { it.value }
            val r = Response(
                startUtc = t0, type = s.courses.first().mealType, carbs = s.totalCarbs, protein = protein, fat = fat,
                hasComposition = protein > 0 || fat > 0, impact = s.courses.maxBy { it.impactType.durationMinutes }.impactType,
                values = values, baseline = baseline, bolusLeadMin = bolus?.let { ((it.timestampUtc - t0) / 60_000L).toInt() }
            )
            if (!r.peak.isNaN()) out += r
        }
        return out
    }

    // -------------------------------------------------------------------------------------------------------------

    private fun scopeLabel(type: MealType?) = when (type) {
        null -> "meals"; MealType.BREAKFAST -> "breakfast"; MealType.LUNCH -> "lunch"; MealType.DINNER -> "dinner"; MealType.SNACK -> "snacks"
    }

    /** Evaluates the whole set first; only when that finds nothing, each meal type with enough meals on its own. */
    private fun scoped(c: Ctx, eval: (Ctx, List<Response>, MealType?) -> InsightCandidate?): List<InsightCandidate> {
        eval(c, c.all, null)?.let { return listOf(it) }
        return MealType.entries.mapNotNull { type ->
            val group = c.all.filter { it.type == type }
            if (group.size < MIN_MEALS_PER_TYPE) null else eval(c, group, type)
        }
    }

    private fun halves(g: List<Response>): Pair<List<Response>, List<Response>> =
        g.sortedBy { it.startUtc }.let { it.take(it.size / 2) to it.drop(it.size / 2) }

    private fun median(x: List<Double>) = x.sorted().let { if (it.isEmpty()) 0.0 else it[it.size / 2] }
    private fun share(n: Int, d: Int) = if (d == 0) 0.0 else n.toDouble() / d
    private fun pct(x: Double) = "${(x * 100).roundToInt()} %"

    private fun mealDay(r: Response, zone: java.time.ZoneId): String {
        val d = Instant.ofEpochMilli(r.startUtc).atZone(zone)
        return "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${r.type.type.lowercase()}"
    }

    private fun after(k: Int): String = (k * BIN_MINUTES).let { m -> if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }

    private fun rank(base: Double, effect: Double, n: Int) = base * (0.6 + 0.4 * effect.coerceIn(0.0, 1.0)) * (0.7 + 0.3 * (n / 20.0).coerceAtMost(1.0))

    private fun limitations(extra: String) = listOf(
        "Meals with another meal close by, too little sensor data, or glucose already low at the start are left out.",
        extra
    )

    private fun card(
        c: Ctx, id: String, detector: String, title: String, summary: String, graph: MiniGraph, n: Int, positive: Int?,
        effect: Double, effectLabel: String, uncertainty: String, limits: List<String>, examples: List<String>,
        tone: InsightTone, priority: Double, baseline: String? = null
    ) = InsightCard(
        id = id, category = InsightCategory.MEAL, detector = detector, title = title, summary = summary, miniGraph = graph,
        observationFromUtc = c.from, observationToUtc = c.to, baselineFromUtc = baseline?.let { c.from }, baselineToUtc = baseline?.let { c.to },
        baselineLabel = baseline, sampleSize = n, sampleUnit = "meals", positiveCount = positive, effectSize = effect,
        effectLabel = effectLabel, uncertainty = uncertainty, limitations = limits, examples = examples, tone = tone, priority = priority
    )

    // ---- single-group findings -----------------------------------------------------------------------------------

    private fun lows(c: Ctx, g: List<Response>, type: MealType?): InsightCandidate? {
        val has = { r: Response -> r.lowBins(c.t.low) >= 3 }
        val lowMeals = g.filter(has)
        if (lowMeals.size < MIN_LOW_MEALS || share(lowMeals.size, g.size) < MIN_LOW_SHARE) return null
        val (a, b) = halves(g)
        if (a.none(has) || b.none(has)) return null
        val s = share(lowMeals.size, g.size)
        val examples = lowMeals.sortedBy { it.minAfter()!!.second }.take(3).map {
            val (k, v) = it.minAfter()!!; "${mealDay(it, c.input.zone)} · ${v.roundToInt()} mg/dL, ${after(k)} after"
        }
        return InsightCandidate(
            card(
                c, "meal.low.${scopeLabel(type)}", "meal-low", "Low glucose after ${scopeLabel(type)}",
                "Glucose went below ${c.t.low} mg/dL between 1 and 4 hours after ${lowMeals.size} of ${g.size} ${scopeLabel(type)}.",
                responseGraph(g, null, scopeLabel(type), null), g.size, lowMeals.size, s, "${lowMeals.size} of ${g.size} meals (${pct(s)})",
                "Seen in both the earlier and the later half of the period.",
                limitations("Insulin from the meal or a correction is likely part of the story but can't be separated out here."),
                examples, InsightTone.UNFAVORABLE, rank(90.0, s / 0.5, g.size)
            ),
            TipTopic.MEAL_LOW, escalate = s >= 0.4
        )
    }

    private fun spikes(c: Ctx, g: List<Response>, type: MealType?): InsightCandidate? {
        val rises = g.map { it.peakRise.toDouble() }
        val med = median(rises)
        val big = g.count { it.peakRise >= SPIKE_RISE }
        if (med < SPIKE_MEDIAN_RISE || share(big, g.size) < SPIKE_MIN_SHARE) return null
        val (a, b) = halves(g)
        if (median(a.map { it.peakRise.toDouble() }) < SPIKE_MEDIAN_RISE * 0.75 || median(b.map { it.peakRise.toDouble() }) < SPIKE_MEDIAN_RISE * 0.75) return null
        val examples = g.sortedByDescending { it.peakRise }.take(3).map {
            "${mealDay(it, c.input.zone)} · up ${it.peakRise.roundToInt()} mg/dL to ${it.peak.roundToInt()}, after ${after(it.peakK)}"
        }
        return InsightCandidate(
            card(
                c, "meal.spike.${scopeLabel(type)}", "meal-spike", "Big rise after ${scopeLabel(type)}",
                "Glucose typically rose ${med.roundToInt()} mg/dL after ${scopeLabel(type)}, and by 50 or more after $big of ${g.size} ${scopeLabel(type)}.",
                responseGraph(g, null, scopeLabel(type), null), g.size, big, med, "median rise ${med.roundToInt()} mg/dL",
                "The typical rise was large in both halves of the period.",
                limitations("Carb amounts come from what was logged, and insulin timing and amount aren't part of this finding."),
                examples, InsightTone.UNFAVORABLE, rank(65.0, (med - SPIKE_MEDIAN_RISE) / 40.0, g.size)
            ),
            TipTopic.MEAL_SPIKE
        )
    }

    private fun slow(c: Ctx, g: List<Response>, type: MealType?): InsightCandidate? {
        val has = { r: Response -> !r.lateMean.isNaN() && r.lateMean > c.t.high }
        val high = g.filter(has)
        if (high.size < 3 || share(high.size, g.size) < SLOW_MIN_SHARE) return null
        val (a, b) = halves(g)
        if (a.none(has) || b.none(has)) return null
        val s = share(high.size, g.size)
        val examples = high.sortedByDescending { it.lateMean }.take(3).map {
            "${mealDay(it, c.input.zone)} · ${it.lateMean.roundToInt()} mg/dL on average 3–4 h after"
        }
        return InsightCandidate(
            card(
                c, "meal.slow.${scopeLabel(type)}", "meal-slow", "Glucose stays high after ${scopeLabel(type)}",
                "Three to four hours after ${high.size} of ${g.size} ${scopeLabel(type)}, glucose was still above ${c.t.high} mg/dL.",
                responseGraph(g, null, scopeLabel(type), null), g.size, high.size, s, "${high.size} of ${g.size} meals (${pct(s)})",
                "Seen in both the earlier and the later half of the period.",
                limitations("Meals that run into each other are excluded, but a late correction or snack can still affect the later hours."),
                examples, InsightTone.UNFAVORABLE, rank(60.0, s / 0.6, g.size)
            ),
            TipTopic.MEAL_SLOW
        )
    }

    private fun good(c: Ctx, g: List<Response>, type: MealType?): InsightCandidate? {
        if (g.size < MIN_MEALS) return null
        val med = median(g.map { it.peakRise.toDouble() })
        val smooth = g.count { it.peak <= c.t.high && !it.lateMean.isNaN() && abs(it.lateRise) < 20 && it.lowBins(c.t.low) < 3 }
        val s = share(smooth, g.size)
        if (med < GOOD_MIN_RISE || med > GOOD_MAX_RISE || s < GOOD_MIN_SHARE) return null
        return InsightCandidate(
            card(
                c, "meal.good.${scopeLabel(type)}", "meal-good", "Smooth glucose after ${scopeLabel(type)}",
                "After $smooth of ${g.size} ${scopeLabel(type)} glucose rose gently (typically ${med.roundToInt()} mg/dL), stayed in range and settled back within 4 hours.",
                responseGraph(g, null, scopeLabel(type), null), g.size, smooth, s, "${pct(s)} of meals smooth",
                "Consistent across the period.",
                listOf("Meals with another meal close by, too little sensor data, or glucose already low at the start are left out."),
                emptyList(), InsightTone.FAVORABLE, rank(30.0, (s - GOOD_MIN_SHARE) / 0.3, g.size)
            ),
            TipTopic.MEAL_GOOD
        )
    }

    // ---- comparisons -----------------------------------------------------------------------------------------------

    private fun variance(x: List<Double>) = if (x.size < 2) 0.0 else x.sumOf { (it - x.average()).let { d -> d * d } } / (x.size - 1)

    /** Mean difference a - b and its standard error. */
    private fun welch(a: List<Double>, b: List<Double>): Pair<Double, Double> =
        (a.average() - b.average()) to sqrt(variance(a) / a.size + variance(b) / b.size)

    private fun rich(c: Ctx): List<InsightCandidate> {
        val withComp = c.all.filter { it.hasComposition }
        val byComposition = withComp.partition { it.fat + it.protein >= RICH_THRESHOLD_G }
        val usable = byComposition.first.size >= MIN_GROUP && byComposition.second.size >= MIN_GROUP
        val (rich, light, basis) = if (usable) Triple(byComposition.first, byComposition.second, "recorded fat and protein")
        else {
            val slowLogged = c.all.partition { it.impact == ImpactType.LONG }
            Triple(slowLogged.first, slowLogged.second, "the absorption you logged")
        }
        if (rich.size < MIN_GROUP || light.size < MIN_GROUP) return emptyList()
        val lateRich = rich.filter { !it.lateMean.isNaN() }.map { it.lateRise.toDouble() }
        val lateLight = light.filter { !it.lateMean.isNaN() }.map { it.lateRise.toDouble() }
        if (lateRich.size < MIN_GROUP || lateLight.size < MIN_GROUP) return emptyList()
        val (diff, se) = welch(lateRich, lateLight)
        if (diff < RICH_MIN_DIFF || diff < SE_FACTOR * se) return emptyList()
        val composition = usable
        val groupName = if (composition) "Richer meals" else "Slow-absorbing meals"
        val limits = limitations(
            if (composition) "Richer means 35 g or more of fat and protein combined; meals without recorded fat or protein aren't compared."
            else "Fat and protein weren't recorded often enough, so this uses the absorption you logged for each meal, which is less reliable than recorded composition."
        ) + "Richer meals are often bigger meals as well, so size and composition can't be told apart here."
        return listOf(
            InsightCandidate(
                card(
                    c, "meal.rich", "meal-rich", if (composition) "Richer meals keep glucose up longer" else "Slow-absorbing meals keep glucose up longer",
                    "3–4 hours after $groupName (${rich.size}) glucose was ${diff.roundToInt()} mg/dL higher above its starting level than after the others (${light.size}).",
                    responseGraph(rich, light, groupName, "Other meals"), rich.size + light.size, rich.size, diff, "+${diff.roundToInt()} mg/dL at 3–4 h",
                    "The difference is about ${"%.1f".format(diff / se)}× its likely margin of error (±${se.roundToInt()} mg/dL); based on $basis.",
                    limits, emptyList(), InsightTone.UNFAVORABLE,
                    rank(55.0, diff / 50.0, rich.size + light.size) * if (composition) 1.0 else 0.8, baseline = "Other meals in the same period"
                ),
                TipTopic.MEAL_RICH
            )
        )
    }

    private fun typeDifferences(c: Ctx): List<InsightCandidate> = MealType.entries.mapNotNull { type ->
        val g = c.all.filter { it.type == type }
        val rest = c.all.filter { it.type != type }
        if (g.size < MIN_MEALS_PER_TYPE || rest.size < MIN_MEALS_PER_TYPE) return@mapNotNull null
        val (diff, se) = welch(g.map { it.peakRise.toDouble() }, rest.map { it.peakRise.toDouble() })
        if (diff < TYPE_MIN_DIFF || diff < SE_FACTOR * se) return@mapNotNull null
        InsightCandidate(
            card(
                c, "meal.type.${scopeLabel(type)}", "meal-type", "Bigger rises after ${scopeLabel(type)}",
                "After ${scopeLabel(type)} (${g.size}) glucose rose ${diff.roundToInt()} mg/dL more than after your other meals (${rest.size}).",
                responseGraph(g, rest, scopeLabel(type).replaceFirstChar { it.uppercase() }, "Other meals"), g.size + rest.size, g.size, diff,
                "+${diff.roundToInt()} mg/dL rise", "The difference is about ${"%.1f".format(diff / se)}× its likely margin of error (±${se.roundToInt()} mg/dL).",
                limitations("What you eat at this meal, its size and the insulin used can all differ from your other meals."),
                emptyList(), InsightTone.UNFAVORABLE, rank(45.0, diff / 60.0, g.size + rest.size), baseline = "Your other meals in the same period"
            ),
            TipTopic.MEAL_TYPE
        )
    }

    private fun bolusTiming(c: Ctx): List<InsightCandidate> {
        val dosed = c.all.filter { it.bolusLeadMin != null && it.carbs >= 20 }
        val (early, other) = dosed.partition { it.bolusLeadMin!! <= -EARLY_BOLUS_MIN }
        if (early.size < MIN_GROUP || other.size < MIN_GROUP) return emptyList()
        val ca = median(early.map { it.carbs.toDouble() }); val cb = median(other.map { it.carbs.toDouble() })
        if (abs(ca - cb) / maxOf(ca, cb) > SIMILAR_CARBS) return emptyList() // not comparable meals
        val (diff, se) = welch(other.map { it.peakRise.toDouble() }, early.map { it.peakRise.toDouble() })
        if (diff < BOLUS_MIN_DIFF || diff < SE_FACTOR * se) return emptyList()
        return listOf(
            InsightCandidate(
                card(
                    c, "meal.bolus", "meal-bolus", "Smaller rises when insulin comes first",
                    "When insulin was given at least $EARLY_BOLUS_MIN minutes before eating (${early.size} meals), glucose rose ${diff.roundToInt()} mg/dL less than when it was given later (${other.size}).",
                    responseGraph(early, other, "Insulin before the meal", "Insulin at or after"), early.size + other.size, early.size, diff,
                    "${diff.roundToInt()} mg/dL smaller rise", "The difference is about ${"%.1f".format(diff / se)}× its likely margin of error (±${se.roundToInt()} mg/dL); typical carbs were similar (${ca.roundToInt()} vs ${cb.roundToInt()} g).",
                    limitations("Insulin is often given in response to how glucose looks, so this may reflect why it was given when it was, not only the timing. It says nothing about how much to give.") ,
                    emptyList(), InsightTone.MIXED, rank(40.0, diff / 50.0, early.size + other.size), baseline = "Meals with insulin at or after eating"
                ),
                TipTopic.MEAL_BOLUS
            )
        )
    }

    // ---- graph ----------------------------------------------------------------------------------------------------

    private fun responseGraph(g: List<Response>, compare: List<Response>?, aLabel: String, bLabel: String?): MiniGraph =
        eventGraph(
            g.map { EventCurve(it.values, it.baseline) }, compare?.map { EventCurve(it.values, it.baseline) },
            FIRST_K, LAST_K, GRAPH_STEP, aLabel, bLabel, "meal", null, listOf(0, 60, 120, 180, 240)
        )
}
