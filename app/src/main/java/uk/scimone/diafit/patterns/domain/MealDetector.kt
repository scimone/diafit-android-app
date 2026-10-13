package uk.scimone.diafit.patterns.domain

import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealType
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
    internal const val FIRST_K = -6
    internal const val LAST_K = 48
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
    /** "Richer" = at least the median fat + protein of the user's own meals (and clearly richer than the rest). */
    private const val RICH_MIN_CUT_G = 10
    private const val RICH_RATIO = 1.3
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

        fun at(k: Int): Long = startUtc + k * BIN_MINUTES * 60_000L

        /**
         * This meal as an example: drawn from an hour before (so insulin given ahead shows) to 4.5 h after, with
         * [focusFromMin]..[focusToMin] after the start shaded and [markK] (5-minute steps from the start) marked.
         */
        fun case(focusFromMin: Int, focusToMin: Int, note: Msg, markK: Int? = null, group: Msg? = null) =
            Cases.event(startUtc, startUtc, msg("pat_meal_event", ref("meal_type_${type.name.lowercase()}"), carbs), 60, 270, focusFromMin, focusToMin, note, markK?.let(::at), group)
    }

    fun detect(input: DetectionInput): List<PatternCandidate> {
        val responses = responses(input)
        if (responses.size < MIN_MEALS) return emptyList()
        val c = Ctx(input, responses)
        val out = ArrayList<PatternCandidate>()
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
        // Effect-size thresholds follow this person's glucose variability.
        val spikeMedian = input.scaled(SPIKE_MEDIAN_RISE)
        val spikeRise = input.scaled(SPIKE_RISE)
        val richDiff = input.scaled(RICH_MIN_DIFF)
        val typeDiff = input.scaled(TYPE_MIN_DIFF)
        val bolusDiff = input.scaled(BOLUS_MIN_DIFF)
    }

    // -------------------------------------------------------------------------------------------------------------

    internal fun responses(input: DetectionInput, windowDays: Int = WINDOW_DAYS): List<Response> {
        val lookup = GlucoseLookup(input.days, input.zone)
        val low = input.thresholds.low
        val since = input.startOfDayUtc(input.today.minusDays((windowDays - 1).toLong()))
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

    /** The scope as it appears in stored ids (stable, English). */
    private fun scopeLabel(type: MealType?) = when (type) {
        null -> "meals"; MealType.BREAKFAST -> "breakfast"; MealType.LUNCH -> "lunch"; MealType.DINNER -> "dinner"; MealType.SNACK -> "snacks"
    }

    private fun scopeKey(type: MealType?) = when (type) {
        null -> "meals"; MealType.BREAKFAST -> "breakfast"; MealType.LUNCH -> "lunch"; MealType.DINNER -> "dinner"; MealType.SNACK -> "snack"
    }

    /** "after <this>": the scope with the article it needs after "after". */
    private fun afterScope(type: MealType?) = ref("pat_scope_${scopeKey(type)}_after")
    private fun pluralScope(type: MealType?) = ref("pat_scope_${scopeKey(type)}_pl")
    private fun labelScope(type: MealType?) = Msg("pat_scope_${scopeKey(type)}_label")

    /** Evaluates the whole set first; only when that finds nothing, each meal type with enough meals on its own. */
    private fun scoped(c: Ctx, eval: (Ctx, List<Response>, MealType?) -> PatternCandidate?): List<PatternCandidate> {
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

    private fun after(k: Int): String = (k * BIN_MINUTES).let { m -> if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min" }

    private fun dec1(x: Double) = "%.1f".format(java.util.Locale.ROOT, x)

    private fun rank(base: Double, effect: Double, n: Int) = base * (0.6 + 0.4 * effect.coerceIn(0.0, 1.0)) * (0.7 + 0.3 * (n / 20.0).coerceAtMost(1.0))

    private fun limitations(extra: Msg) = listOf(msg("pat_meal_lim_excluded"), extra)

    private fun card(
        c: Ctx, id: String, detector: String, title: Msg, summary: Msg, graph: MiniGraph, n: Int, positive: Int?,
        effect: Double, effectLabel: Msg, uncertainty: Msg, limits: List<Msg>, examples: List<PatternExample>,
        tone: PatternTone, priority: Double, baseline: Msg? = null, countLabel: Boolean = false
    ) = PatternCard(
        id = id, category = PatternCategory.MEAL, detector = detector, title = title, summary = summary, miniGraph = graph,
        observationFromUtc = c.from, observationToUtc = c.to, baselineFromUtc = baseline?.let { c.from }, baselineToUtc = baseline?.let { c.to },
        baselineLabel = baseline, sampleSize = n, sampleUnit = SampleUnit.MEALS, positiveCount = positive, effectSize = effect,
        effectLabel = effectLabel, effectIsCount = countLabel, uncertainty = uncertainty, limitations = limits, cases = examples, tone = tone, priority = priority
    )

    // ---- single-group findings -----------------------------------------------------------------------------------

    private fun lows(c: Ctx, g: List<Response>, type: MealType?): PatternCandidate? {
        val has = { r: Response -> r.lowBins(c.t.low) >= 3 }
        val lowMeals = g.filter(has)
        if (lowMeals.size < MIN_LOW_MEALS || share(lowMeals.size, g.size) < MIN_LOW_SHARE) return null
        val (a, b) = halves(g)
        if (a.none(has) || b.none(has)) return null
        val s = share(lowMeals.size, g.size)
        val examples = lowMeals.sortedBy { it.minAfter()!!.second }.take(Cases.SINGLE).map {
            val (k, v) = it.minAfter()!!
            it.case(60, 240, msg("pat_meal_low_case", v.roundToInt(), after(k)), k)
        }
        return PatternCandidate(
            card(
                c, "meal.low.${scopeLabel(type)}", "meal-low", msg("pat_meal_low_title", afterScope(type)),
                msg("pat_meal_low_summary", c.t.low, lowMeals.size, g.size, pluralScope(type)),
                responseGraph(g, null, labelScope(type), null), g.size, lowMeals.size, s, msg("pat_meal_count_effect", lowMeals.size, g.size, pct(s)),
                msg("pat_meal_unc_halves"),
                limitations(msg("pat_meal_low_lim")),
                examples, PatternTone.UNFAVORABLE, rank(90.0, s / 0.5, g.size), countLabel = true
            ),
            TipTopic.MEAL_LOW, escalate = s >= 0.4
        )
    }

    private fun spikes(c: Ctx, g: List<Response>, type: MealType?): PatternCandidate? {
        val rises = g.map { it.peakRise.toDouble() }
        val med = median(rises)
        val big = g.count { it.peakRise >= c.spikeRise }
        if (med < c.spikeMedian || share(big, g.size) < SPIKE_MIN_SHARE) return null
        val (a, b) = halves(g)
        if (median(a.map { it.peakRise.toDouble() }) < c.spikeMedian * 0.75 || median(b.map { it.peakRise.toDouble() }) < c.spikeMedian * 0.75) return null
        val examples = g.sortedByDescending { it.peakRise }.take(Cases.SINGLE).map {
            it.case(0, 180, msg("pat_meal_spike_case", it.peakRise.roundToInt(), it.peak.roundToInt(), after(it.peakK)), it.peakK)
        }
        return PatternCandidate(
            card(
                c, "meal.spike.${scopeLabel(type)}", "meal-spike", msg("pat_meal_spike_title", afterScope(type)),
                msg("pat_meal_spike_summary", med.roundToInt(), afterScope(type), c.spikeRise.roundToInt(), big, g.size, pluralScope(type)),
                responseGraph(g, null, labelScope(type), null), g.size, big, med, msg("pat_meal_spike_effect", med.roundToInt()),
                msg("pat_meal_spike_unc"),
                limitations(msg("pat_meal_spike_lim")),
                examples, PatternTone.UNFAVORABLE, rank(65.0, (med - c.spikeMedian) / 40.0, g.size)
            ),
            TipTopic.MEAL_SPIKE
        )
    }

    private fun slow(c: Ctx, g: List<Response>, type: MealType?): PatternCandidate? {
        val has = { r: Response -> !r.lateMean.isNaN() && r.lateMean > c.t.high }
        val high = g.filter(has)
        if (high.size < 3 || share(high.size, g.size) < SLOW_MIN_SHARE) return null
        val (a, b) = halves(g)
        if (a.none(has) || b.none(has)) return null
        val s = share(high.size, g.size)
        val examples = high.sortedByDescending { it.lateMean }.take(Cases.SINGLE).map {
            it.case(180, 240, msg("pat_meal_slow_case", it.lateMean.roundToInt()))
        }
        return PatternCandidate(
            card(
                c, "meal.slow.${scopeLabel(type)}", "meal-slow", msg("pat_meal_slow_title", afterScope(type)),
                msg("pat_meal_slow_summary", high.size, g.size, pluralScope(type), c.t.high),
                responseGraph(g, null, labelScope(type), null), g.size, high.size, s, msg("pat_meal_count_effect", high.size, g.size, pct(s)),
                msg("pat_meal_unc_halves"),
                limitations(msg("pat_meal_slow_lim")),
                examples, PatternTone.UNFAVORABLE, rank(60.0, s / 0.6, g.size), countLabel = true
            ),
            TipTopic.MEAL_SLOW
        )
    }

    private fun good(c: Ctx, g: List<Response>, type: MealType?): PatternCandidate? {
        if (g.size < MIN_MEALS) return null
        val med = median(g.map { it.peakRise.toDouble() })
        val smooth = g.count { it.peak <= c.t.high && !it.lateMean.isNaN() && abs(it.lateRise) < 20 && it.lowBins(c.t.low) < 3 }
        val s = share(smooth, g.size)
        if (med < GOOD_MIN_RISE || med > GOOD_MAX_RISE || s < GOOD_MIN_SHARE) return null
        val examples = Cases.typical(g, Cases.SINGLE) { it.peakRise.toDouble() }.sortedByDescending { it.startUtc }.map {
            it.case(0, 240, msg("pat_meal_good_case", it.peakRise.roundToInt(), it.peak.roundToInt()), it.peakK)
        }
        return PatternCandidate(
            card(
                c, "meal.good.${scopeLabel(type)}", "meal-good", msg("pat_meal_good_title", afterScope(type)),
                msg("pat_meal_good_summary", smooth, g.size, pluralScope(type), med.roundToInt()),
                responseGraph(g, null, labelScope(type), null), g.size, smooth, s, msg("pat_meal_good_effect", pct(s)),
                msg("pat_meal_good_unc"),
                listOf(msg("pat_meal_lim_excluded")),
                examples, PatternTone.FAVORABLE, rank(30.0, (s - GOOD_MIN_SHARE) / 0.3, g.size)
            ),
            TipTopic.MEAL_GOOD
        )
    }

    // ---- comparisons -----------------------------------------------------------------------------------------------

    private fun variance(x: List<Double>) = if (x.size < 2) 0.0 else x.sumOf { (it - x.average()).let { d -> d * d } } / (x.size - 1)

    /** Mean difference a - b and its standard error. */
    private fun welch(a: List<Double>, b: List<Double>): Pair<Double, Double> =
        (a.average() - b.average()) to sqrt(variance(a) / a.size + variance(b) / b.size)

    private fun rich(c: Ctx): List<PatternCandidate> {
        val withComp = c.all.filter { it.hasComposition }
        // Rich = at least the median fat + protein of this person's recorded meals, if that splits them clearly.
        val compCut = median(withComp.map { (it.fat + it.protein).toDouble() }).roundToInt()
        val byComposition = withComp.partition { it.fat + it.protein >= compCut }
        val usable = withComp.size >= 2 * MIN_GROUP && compCut >= RICH_MIN_CUT_G &&
            byComposition.first.size >= MIN_GROUP && byComposition.second.size >= MIN_GROUP &&
            median(byComposition.first.map { (it.fat + it.protein).toDouble() }) >= RICH_RATIO * median(byComposition.second.map { (it.fat + it.protein).toDouble() })
        val (rich, light, basis) = if (usable) Triple(byComposition.first, byComposition.second, ref("pat_basis_composition"))
        else {
            val slowLogged = c.all.partition { it.impact == ImpactType.LONG }
            Triple(slowLogged.first, slowLogged.second, ref("pat_basis_logged"))
        }
        if (rich.size < MIN_GROUP || light.size < MIN_GROUP) return emptyList()
        val lateRich = rich.filter { !it.lateMean.isNaN() }.map { it.lateRise.toDouble() }
        val lateLight = light.filter { !it.lateMean.isNaN() }.map { it.lateRise.toDouble() }
        if (lateRich.size < MIN_GROUP || lateLight.size < MIN_GROUP) return emptyList()
        val (diff, se) = welch(lateRich, lateLight)
        if (diff < c.richDiff || diff < SE_FACTOR * se) return emptyList()
        val composition = usable
        val groupLabel = Msg(if (composition) "pat_label_richer" else "pat_label_slowabs")
        val groupName = ref(if (composition) "pat_grp_richer" else "pat_grp_slowabs")
        val otherMeals = Msg("pat_label_other_meals")
        fun late(r: Response, group: Msg) = r.case(
            180, 240,
            if (r.hasComposition) msg("pat_meal_rich_case_comp", Cases.signed(r.lateRise), r.fat + r.protein) else msg("pat_meal_rich_case", Cases.signed(r.lateRise)),
            group = group
        )
        val examples = Cases.typical(rich.filter { !it.lateMean.isNaN() }) { it.lateRise.toDouble() }.map { late(it, groupLabel) } +
            Cases.typical(light.filter { !it.lateMean.isNaN() }) { it.lateRise.toDouble() }.map { late(it, otherMeals) }
        val limits = limitations(
            if (composition) msg("pat_meal_rich_lim_comp", compCut) else msg("pat_meal_rich_lim_logged")
        ) + msg("pat_meal_rich_lim_size")
        return listOf(
            PatternCandidate(
                card(
                    c, "meal.rich", "meal-rich", msg(if (composition) "pat_meal_rich_title" else "pat_meal_slowabs_title"),
                    msg("pat_meal_rich_summary", groupName, rich.size, diff.roundToInt(), light.size),
                    responseGraph(rich, light, groupLabel, otherMeals), rich.size + light.size, rich.size, diff, msg("pat_meal_rich_effect", diff.roundToInt()),
                    msg("pat_meal_margin_basis", dec1(diff / se), se.roundToInt(), basis),
                    limits, examples, PatternTone.UNFAVORABLE,
                    rank(55.0, diff / 50.0, rich.size + light.size) * if (composition) 1.0 else 0.8, baseline = msg("pat_meal_rich_baseline")
                ),
                TipTopic.MEAL_RICH
            )
        )
    }

    private fun typeDifferences(c: Ctx): List<PatternCandidate> = MealType.entries.mapNotNull { type ->
        val g = c.all.filter { it.type == type }
        val rest = c.all.filter { it.type != type }
        if (g.size < MIN_MEALS_PER_TYPE || rest.size < MIN_MEALS_PER_TYPE) return@mapNotNull null
        val (diff, se) = welch(g.map { it.peakRise.toDouble() }, rest.map { it.peakRise.toDouble() })
        if (diff < c.typeDiff || diff < SE_FACTOR * se) return@mapNotNull null
        val aLabel = labelScope(type)
        val otherMeals = Msg("pat_label_other_meals")
        fun rise(r: Response, group: Msg) = r.case(0, 180, msg("pat_meal_type_case", r.peakRise.roundToInt(), after(r.peakK)), r.peakK, group)
        val examples = Cases.typical(g) { it.peakRise.toDouble() }.map { rise(it, aLabel) } +
            Cases.typical(rest) { it.peakRise.toDouble() }.map { rise(it, otherMeals) }
        PatternCandidate(
            card(
                c, "meal.type.${scopeLabel(type)}", "meal-type", msg("pat_meal_type_title", afterScope(type)),
                msg("pat_meal_type_summary", afterScope(type), g.size, diff.roundToInt(), rest.size),
                responseGraph(g, rest, aLabel, otherMeals), g.size + rest.size, g.size, diff,
                msg("pat_meal_type_effect", diff.roundToInt()), msg("pat_meal_margin", dec1(diff / se), se.roundToInt()),
                limitations(msg("pat_meal_type_lim")),
                examples, PatternTone.UNFAVORABLE, rank(45.0, diff / 60.0, g.size + rest.size), baseline = msg("pat_meal_type_baseline")
            ),
            TipTopic.MEAL_TYPE
        )
    }

    private fun bolusTiming(c: Ctx): List<PatternCandidate> {
        val dosed = c.all.filter { it.bolusLeadMin != null && it.carbs >= 20 }
        val (early, other) = dosed.partition { it.bolusLeadMin!! <= -EARLY_BOLUS_MIN }
        if (early.size < MIN_GROUP || other.size < MIN_GROUP) return emptyList()
        val ca = median(early.map { it.carbs.toDouble() }); val cb = median(other.map { it.carbs.toDouble() })
        if (abs(ca - cb) / maxOf(ca, cb) > SIMILAR_CARBS) return emptyList() // not comparable meals
        val (diff, se) = welch(other.map { it.peakRise.toDouble() }, early.map { it.peakRise.toDouble() })
        if (diff < c.bolusDiff || diff < SE_FACTOR * se) return emptyList()
        val beforeLabel = Msg("pat_label_insulin_before")
        val afterLabel = Msg("pat_label_insulin_after")
        fun ex(r: Response, group: Msg): PatternExample {
            val m = r.bolusLeadMin!!; val rise = r.peakRise.roundToInt()
            val note = if (m < 0) msg("pat_meal_bolus_case_before", -m, rise) else if (m == 0) msg("pat_meal_bolus_case_with", rise) else msg("pat_meal_bolus_case_after", m, rise)
            return r.case(0, 180, note, r.peakK, group)
        }
        val examples = Cases.typical(early) { it.peakRise.toDouble() }.map { ex(it, beforeLabel) } +
            Cases.typical(other) { it.peakRise.toDouble() }.map { ex(it, afterLabel) }
        return listOf(
            PatternCandidate(
                card(
                    c, "meal.bolus", "meal-bolus", msg("pat_meal_bolus_title"),
                    msg("pat_meal_bolus_summary", EARLY_BOLUS_MIN, early.size, diff.roundToInt(), other.size),
                    responseGraph(early, other, beforeLabel, afterLabel), early.size + other.size, early.size, diff,
                    msg("pat_meal_bolus_effect", diff.roundToInt()), msg("pat_meal_bolus_unc", dec1(diff / se), se.roundToInt(), ca.roundToInt(), cb.roundToInt()),
                    limitations(msg("pat_meal_bolus_lim")),
                    examples, PatternTone.MIXED, rank(40.0, diff / 50.0, early.size + other.size), baseline = msg("pat_meal_bolus_baseline")
                ),
                TipTopic.MEAL_BOLUS
            )
        )
    }

    // ---- graph ----------------------------------------------------------------------------------------------------

    private fun responseGraph(g: List<Response>, compare: List<Response>?, aLabel: Msg, bLabel: Msg?): MiniGraph =
        eventGraph(
            g.map { EventCurve(it.values, it.baseline) }, compare?.map { EventCurve(it.values, it.baseline) },
            FIRST_K, LAST_K, GRAPH_STEP, aLabel, bLabel, Msg(EVENT_MEAL), null, listOf(0, 60, 120, 180, 240)
        )
}
