package uk.scimone.diafit.mealactivity.domain

import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealSitting
import uk.scimone.diafit.core.domain.model.toSittings
import kotlin.math.ceil

/**
 * A validated meal + CGM episode. [observed] is the measured glucose change (mg/dL) from the pre-meal baseline per grid
 * point (`NaN` = no measurement or masked); index 0 is 0 by definition. Real readings only: nothing is interpolated or
 * filled, and no generated curve ever enters here. Bins average the readings within ±step/2 of the grid time.
 */
class TrainingExample(
    val key: String,
    val startUtc: Long,
    val courses: List<CourseSpec>,
    val baselineMgdl: Double,
    val observed: DoubleArray,
    val coverage: Double,
    val fingerprint: String
) {
    val totalCarbs: Double get() = courses.sumOf { it.input.carbsG ?: 0.0 }
    /** True when every course's protein and fat are known. */
    val hasComposition: Boolean get() = courses.all { it.input.proteinG != null && it.input.fatG != null }
    /** Fat+protein energy relative to carbohydrate energy, the composition spread used to judge identifiability. */
    val compositionRatio: Double?
        get() = if (!hasComposition || totalCarbs <= 0.0) null
        else courses.sumOf { 9 * (it.input.fatG ?: 0.0) + 4 * (it.input.proteinG ?: 0.0) } / (4 * totalCarbs)
}

enum class Exclusion {
    INVALID_INPUT, TOO_FEW_CARBS, EXTENDED_MEAL, HORIZON_NOT_ELAPSED, PRIOR_MEAL, NEXT_MEAL_TOO_SOON,
    NO_BASELINE, UNSTABLE_BASELINE, LOW_BASELINE, LOW_COVERAGE, LARGE_GAP, DUPLICATE
}

sealed interface BuildResult {
    data class Included(val example: TrainingExample) : BuildResult
    data class Excluded(val reason: Exclusion) : BuildResult
}

data class TrainingSet(val examples: List<TrainingExample>, val excluded: Map<Exclusion, Int>, val revision: String)

object TrainingExampleBuilder {
    @PublishedApi internal const val MIN = 60_000L

    /** Median of valid readings in [start - window, start], or a reason why there is no usable baseline. */
    fun baseline(readings: List<CgmEntity>, startUtc: Long, rules: TrainingRules, requireStable: Boolean): Pair<Double?, Exclusion?> {
        val pre = readings.filter {
            it.timestamp in (startUtc - rules.baselineWindowMin * MIN)..startUtc && it.valueMgdl in rules.minValidMgdl..rules.maxValidMgdl
        }.sortedBy { it.timestamp }
        if (pre.size < 2 || startUtc - pre.last().timestamp > rules.maxBaselineGapMin * MIN) return null to Exclusion.NO_BASELINE
        val v = pre.map { it.valueMgdl.toDouble() }.sorted()
        if (requireStable && v.last() - v.first() > rules.maxBaselineRangeMgdl) return null to Exclusion.UNSTABLE_BASELINE
        val median = if (v.size % 2 == 1) v[v.size / 2] else (v[v.size / 2 - 1] + v[v.size / 2]) / 2
        if (requireStable && median < rules.minBaselineMgdl) return null to Exclusion.LOW_BASELINE
        return median to null
    }

    /**
     * @param prevEndUtc end (last course) of the previous meal with >= 10 g, null if none known
     * @param nextStartUtc start of the next such meal
     * @param readings CGM readings covering at least start-baselineWindow .. start+horizon (any extra is ignored)
     */
    fun build(
        courses: List<MealEntity>, prevEndUtc: Long?, nextStartUtc: Long?, readings: List<CgmEntity>,
        nowUtc: Long, grid: CurveGrid = CurveGrid(), rules: TrainingRules = TrainingRules()
    ): BuildResult {
        val sorted = courses.sortedBy { it.mealTimeUtc }
        val start = sorted.first().mealTimeUtc
        if (sorted.last().mealTimeUtc - start > 60 * MIN) return BuildResult.Excluded(Exclusion.EXTENDED_MEAL)
        val specs = sorted.map { CourseSpec(((it.mealTimeUtc - start) / MIN).toInt(), it.toMealInput()) }
        if (specs.any { it.input.validate() !is InputCheck.Valid }) return BuildResult.Excluded(Exclusion.INVALID_INPUT)
        if (specs.sumOf { it.input.carbsG ?: 0.0 } < InputLimits.MIN_TRAINING_CARBS_G) return BuildResult.Excluded(Exclusion.TOO_FEW_CARBS)
        if (prevEndUtc != null && start - prevEndUtc < rules.priorMealExclusionMin * MIN) return BuildResult.Excluded(Exclusion.PRIOR_MEAL)

        val cleanSpan = minOf(grid.horizonMinutes.toLong(), nextStartUtc?.let { (it - start) / MIN } ?: Long.MAX_VALUE).toInt()
        if (cleanSpan < rules.minCleanSpanMin) return BuildResult.Excluded(Exclusion.NEXT_MEAL_TOO_SOON)
        if (nowUtc < start + (cleanSpan + 10) * MIN) return BuildResult.Excluded(Exclusion.HORIZON_NOT_ELAPSED)

        val (base, why) = baseline(readings, start, rules, requireStable = true)
        if (base == null) return BuildResult.Excluded(why!!)

        val valid = readings.filter { it.valueMgdl in rules.minValidMgdl..rules.maxValidMgdl }
        val half = grid.stepMinutes * MIN / 2
        val observed = DoubleArray(grid.size) { Double.NaN }
        observed[0] = 0.0
        var expected = 0; var got = 0; var run = 0; var longestGap = 0
        for (k in 1 until grid.size) {
            // The next meal's own start is already its influence: everything from there on is masked.
            if (grid.minuteAt(k) > cleanSpan || (cleanSpan < grid.horizonMinutes && grid.minuteAt(k) >= cleanSpan)) break
            expected++
            val t = start + grid.minuteAt(k) * MIN
            val bin = valid.filter { it.timestamp >= t - half && it.timestamp < t + half }
            if (bin.isEmpty()) { run++; longestGap = maxOf(longestGap, run) }
            else { run = 0; got++; observed[k] = bin.map { it.valueMgdl }.average() - base }
        }
        val coverage = if (expected == 0) 0.0 else got.toDouble() / expected
        if (coverage < rules.minCoverage) return BuildResult.Excluded(Exclusion.LOW_COVERAGE)
        if (longestGap > rules.maxConsecutiveMissingBins) return BuildResult.Excluded(Exclusion.LARGE_GAP)

        val fp = Fingerprint.of(
            ActivityVersions.algoKey(grid), specs.map { c ->
                listOf(c.offsetMin, Fingerprint.num(c.input.carbsG), Fingerprint.num(c.input.proteinG), Fingerprint.num(c.input.fatG),
                    Fingerprint.num(c.input.fiberG), c.input.speed)
            }, start, Fingerprint.num(base), observed.map { Fingerprint.num(it) }, cleanSpan
        )
        return BuildResult.Included(TrainingExample(sorted.first().sittingId ?: "m${sorted.first().id}", start, specs, base, observed, coverage, fp))
    }

    /**
     * Builds the training set from persisted meals (valid, unmerged) and a reader of persisted CGM. Overlap policy: a
     * previous meal (>= 10 g) ending less than [TrainingRules.priorMealExclusionMin] before excludes the episode; a next
     * meal masks everything from its start on and must leave a clean span of at least [TrainingRules.minCleanSpanMin].
     * Duplicate episodes (same start, same totals) count once.
     */
    inline fun buildAll(
        meals: List<MealEntity>, nowUtc: Long, grid: CurveGrid = CurveGrid(), rules: TrainingRules = TrainingRules(),
        readingsBetween: (fromUtc: Long, toUtc: Long) -> List<CgmEntity>
    ): TrainingSet {
        val excluded = HashMap<Exclusion, Int>()
        val sittings: List<MealSitting> = meals.sortedBy { it.mealTimeUtc }.toSittings()
        val significant = sittings.filter { it.totalCarbs >= InputLimits.MIN_TRAINING_CARBS_G }
        val seen = HashSet<String>()
        val examples = ArrayList<TrainingExample>()
        // Newest first so the episode cap keeps recent history; re-sorted by time at the end.
        for (s in sittings.asReversed()) {
            if (s.startTime < nowUtc - rules.windowDays * 86_400_000L) continue
            if (examples.size >= rules.maxEpisodes) break
            if (!seen.add("${s.startTime}-${s.totalCarbs}-${s.courses.size}")) { excluded.merge(Exclusion.DUPLICATE, 1, Int::plus); continue }
            val prev = significant.lastOrNull { it.startTime < s.startTime }
            val next = significant.firstOrNull { it.startTime > s.startTime }
            val readings = readingsBetween(s.startTime - rules.baselineWindowMin * MIN, s.startTime + (grid.horizonMinutes + 10) * MIN)
            when (val r = build(s.courses, prev?.endTime, next?.startTime, readings, nowUtc, grid, rules)) {
                is BuildResult.Included -> examples += r.example
                is BuildResult.Excluded -> excluded.merge(r.reason, 1, Int::plus)
            }
        }
        examples.sortBy { it.startUtc }
        return TrainingSet(examples, excluded, revisionOf(examples))
    }

    /** Order-independent revision of a set of episodes (what the model would be trained on). */
    fun revisionOf(examples: List<TrainingExample>): String = Fingerprint.of(examples.map { it.fingerprint }.sorted())
}
