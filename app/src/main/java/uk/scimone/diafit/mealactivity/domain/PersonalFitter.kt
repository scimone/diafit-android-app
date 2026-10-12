package uk.scimone.diafit.mealactivity.domain

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/** Fit quality of the *modelled timing* against CGM (per-episode gain free, so amplitude never counts), mg/dL. */
@Serializable
data class ShapeMetrics(val n: Int, val mae: Double, val rmse: Double, val peakTimeErrorMin: Double)

@Serializable
data class TrainReport(
    val trainEpisodes: Int, val holdoutEpisodes: Int, val compositionEstimated: Boolean,
    val baselineHoldout: ShapeMetrics?, val candidateHoldout: ShapeMetrics?, val note: String
)

sealed interface TrainOutcome {
    /** Not enough valid paired episodes (or too little variety): keep the baseline and wait for more data. */
    data class Ineligible(val reason: String, val episodes: Int) : TrainOutcome
    /** A candidate was fitted but did not pass validation: keep the baseline (or the previous model). */
    data class Rejected(val reason: String, val report: TrainReport) : TrainOutcome
    data class Accepted(val adjust: PersonalAdjust, val report: TrainReport) : TrainOutcome
}

/**
 * Calibrates the timing parameters of the activity model ([PersonalAdjust]) from episodes, through the fixed
 * [ObservationModel]. The latent activity is the target; glucose is only indirect evidence. A small bounded parameter set
 * is fitted by deterministic pattern search on a robust (Huber) loss plus a prior toward the baseline.
 */
object PersonalFitter {
    private const val GAIN_MAX = 12.0 // mg/dL per gram, generous; only keeps a degenerate episode from exploding

    /** Per-episode least-squares gain on the valid points, then the Huber mean of the residuals, and the fitted shape. */
    private fun episodeFit(ex: TrainingExample, shape: DoubleArray, huber: Double): Pair<Double, Double> {
        var so = 0.0; var sp = 0.0; var n = 0
        for (k in 1 until shape.size) { val o = ex.observed[k]; if (!o.isNaN()) { so += o * shape[k]; sp += shape[k] * shape[k]; n++ } }
        if (n == 0) return 0.0 to 0.0
        val gain = if (sp > 1e-12) (so / sp).coerceIn(0.0, GAIN_MAX) else 0.0
        var loss = 0.0
        for (k in 1 until shape.size) {
            val o = ex.observed[k]; if (o.isNaN()) continue
            val r = abs(o - gain * shape[k])
            loss += if (r <= huber) 0.5 * r * r / huber else r - 0.5 * huber
        }
        return loss / n to gain
    }

    private fun engine(a: PersonalAdjust, params: ActivityParams, grid: CurveGrid) = MealActivityEngine(params, a, grid)

    fun dataLoss(examples: List<TrainingExample>, a: PersonalAdjust, params: ActivityParams, grid: CurveGrid, cfg: PersonalizationConfig): Double {
        val e = engine(a, params, grid)
        return examples.sumOf { episodeFit(it, ObservationModel.shape(it.courses, e), cfg.huberDeltaMgdl).first } / examples.size
    }

    private fun objective(examples: List<TrainingExample>, a: PersonalAdjust, free: Boolean, params: ActivityParams, grid: CurveGrid, cfg: PersonalizationConfig, priorScale: Double): Double {
        val prior = (cfg.priorEpisodes / examples.size) * priorScale * (
            sq(ln(a.timeScale) / cfg.sigmaLogTimeScale) + sq(a.onsetShiftMin / cfg.sigmaOnsetMin) +
                (if (free) sq((a.compositionGain - 1.0) / cfg.sigmaCompositionGain) else 0.0))
        return dataLoss(examples, a, params, grid, cfg) + prior
    }

    private fun sq(x: Double) = x * x

    private fun clamp(a: PersonalAdjust, cfg: PersonalizationConfig, estimateComposition: Boolean) = PersonalAdjust(
        a.timeScale.coerceIn(cfg.timeScaleMin, cfg.timeScaleMax),
        a.onsetShiftMin.coerceIn(cfg.onsetShiftMinMin, cfg.onsetShiftMaxMin),
        if (estimateComposition) a.compositionGain.coerceIn(cfg.compositionGainMin, cfg.compositionGainMax) else 1.0
    )

    /** Bounded coordinate pattern search from the baseline. Deterministic: same episodes give the same result. */
    fun fit(
        examples: List<TrainingExample>, estimateComposition: Boolean, params: ActivityParams = ActivityParams(),
        grid: CurveGrid = CurveGrid(), cfg: PersonalizationConfig = PersonalizationConfig()
    ): PersonalAdjust {
        // Onset and width trade off against each other (a long ridge), so a coordinate search from the baseline alone can
        // stall: start from the best point of a coarse, deterministic grid (which includes the baseline itself).
        // The prior is worth `priorEpisodes` episodes of the baseline's own loss at one sigma (same units as the data term).
        val priorScale = dataLoss(examples, PersonalAdjust(), params, grid, cfg)
        var best = PersonalAdjust()
        var bestJ = objective(examples, best, estimateComposition, params, grid, cfg, priorScale)
        for (ts in doubleArrayOf(0.75, 1.0, 1.25, 1.5, 1.75)) for (shift in doubleArrayOf(-5.0, 0.0, 8.0, 16.0, 24.0)) {
            val cand = clamp(PersonalAdjust(ts, shift, 1.0), cfg, estimateComposition)
            val j = objective(examples, cand, estimateComposition, params, grid, cfg, priorScale)
            if (j < bestJ - 1e-9) { best = cand; bestJ = j }
        }
        var steps = doubleArrayOf(0.15, 5.0, 0.3) // ln timeScale, onset minutes, composition gain
        val tol = doubleArrayOf(0.01, 0.5, 0.02)
        var iter = 0
        while (iter++ < 60 && (0..2).any { steps[it] >= tol[it] && (it != 2 || estimateComposition) }) {
            var improved = false
            for (dim in 0..2) {
                if (dim == 2 && !estimateComposition) continue
                for (sign in intArrayOf(1, -1)) {
                    val cand = clamp(
                        when (dim) {
                            0 -> best.copy(timeScale = best.timeScale * kotlin.math.exp(sign * steps[0]))
                            1 -> best.copy(onsetShiftMin = best.onsetShiftMin + sign * steps[1])
                            else -> best.copy(compositionGain = best.compositionGain + sign * steps[2])
                        }, cfg, estimateComposition
                    )
                    if (cand == best) continue
                    val j = objective(examples, cand, estimateComposition, params, grid, cfg, priorScale)
                    if (j < bestJ - 1e-9) { best = cand; bestJ = j; improved = true }
                }
            }
            if (!improved) steps = DoubleArray(3) { steps[it] / 2 }
        }
        return best
    }

    fun evaluate(examples: List<TrainingExample>, a: PersonalAdjust, params: ActivityParams, grid: CurveGrid, cfg: PersonalizationConfig): ShapeMetrics {
        val e = engine(a, params, grid)
        var abs = 0.0; var sq = 0.0; var pts = 0; var peakErr = 0.0; var peaks = 0
        for (ex in examples) {
            val shape = ObservationModel.shape(ex.courses, e)
            val (_, gain) = episodeFit(ex, shape, cfg.huberDeltaMgdl)
            var oi = -1; var pi = -1
            for (k in 1 until shape.size) {
                val o = ex.observed[k]; if (o.isNaN()) continue
                val r = o - gain * shape[k]
                abs += abs(r); sq += r * r; pts++
                if (oi < 0 || o > ex.observed[oi]) oi = k
                if (pi < 0 || shape[k] > shape[pi]) pi = k
            }
            if (oi >= 0 && pi >= 0) { peakErr += abs(oi - pi) * grid.stepMinutes; peaks++ }
        }
        if (pts == 0) return ShapeMetrics(examples.size, Double.NaN, Double.NaN, Double.NaN)
        return ShapeMetrics(examples.size, abs / pts, sqrt(sq / pts), if (peaks == 0) Double.NaN else peakErr / peaks)
    }
}

/** Eligibility, time-ordered validation and the promote/reject decision. */
object PersonalTrainer {
    private fun cv(x: List<Double>): Double {
        if (x.size < 2) return 0.0
        val m = x.average()
        return if (m <= 0.0) 0.0 else sqrt(x.sumOf { (it - m) * (it - m) } / x.size) / m
    }

    fun train(
        examplesIn: List<TrainingExample>, params: ActivityParams = ActivityParams(), grid: CurveGrid = CurveGrid(),
        cfg: PersonalizationConfig = PersonalizationConfig()
    ): TrainOutcome {
        val examples = examplesIn.sortedBy { it.startUtc }
        val n = examples.size
        if (n < cfg.minExamples) return TrainOutcome.Ineligible("only $n of ${cfg.minExamples} valid meal episodes", n)
        if (cv(examples.map { it.totalCarbs }) < cfg.minCarbCv) return TrainOutcome.Ineligible("meals too similar in carbohydrate", n)

        val holdoutN = max(cfg.minHoldout, ceil(n * cfg.holdoutFraction).toInt())
        val train = examples.dropLast(holdoutN) // time-ordered: validation meals are strictly later than every training meal
        val holdout = examples.takeLast(holdoutN)
        if (train.size < cfg.minExamples - cfg.minHoldout) return TrainOutcome.Ineligible("too few episodes to keep a validation set", n)

        fun composition(set: List<TrainingExample>): Boolean {
            val ratios = set.mapNotNull { it.compositionRatio }
            return ratios.size >= cfg.minCompositionEpisodes && cv(ratios) >= cfg.minCompositionCv
        }
        val estimateComposition = composition(train)

        val candidate = PersonalFitter.fit(train, estimateComposition, params, grid, cfg)
        val base = PersonalFitter.evaluate(holdout, PersonalAdjust(), params, grid, cfg)
        val cand = PersonalFitter.evaluate(holdout, candidate, params, grid, cfg)
        fun report(note: String) = TrainReport(train.size, holdout.size, estimateComposition, base, cand, note)
        fun reject(why: String) = TrainOutcome.Rejected(why, report(why))

        if (listOf(candidate.timeScale, candidate.onsetShiftMin, candidate.compositionGain).any { !it.isFinite() } ||
            listOf(cand.mae, cand.rmse, base.mae, base.rmse).any { !it.isFinite() }) return reject("invalid candidate")
        // A parameter pinned at a bound means the data cannot place it: not identifiable.
        if (candidate.timeScale <= cfg.timeScaleMin * 1.01 || candidate.timeScale >= cfg.timeScaleMax * 0.99 ||
            candidate.onsetShiftMin <= cfg.onsetShiftMinMin + 0.3 || candidate.onsetShiftMin >= cfg.onsetShiftMaxMin - 0.3) {
            return reject("a timing parameter is at its bound (not identifiable)")
        }
        if (cand.mae > base.mae * cfg.requiredMaeRatio) return reject("not clearly better than the baseline on held-out meals")
        if (cand.rmse > base.rmse * cfg.maxRmseRatio) return reject("held-out RMSE worse than the baseline")
        if (!cand.peakTimeErrorMin.isNaN() && !base.peakTimeErrorMin.isNaN() && cand.peakTimeErrorMin > base.peakTimeErrorMin + cfg.maxPeakTimeWorseMin) {
            return reject("held-out peak timing worse than the baseline")
        }

        // Stability: refit without every 5th training episode.
        val perturbed = PersonalFitter.fit(train.filterIndexed { i, _ -> i % 5 != 4 }, estimateComposition, params, grid, cfg)
        if (abs(ln(perturbed.timeScale / candidate.timeScale)) > cfg.maxLogTimeScaleShift ||
            abs(perturbed.onsetShiftMin - candidate.onsetShiftMin) > cfg.maxOnsetShiftMin ||
            abs(perturbed.compositionGain - candidate.compositionGain) > cfg.maxCompositionShift) {
            return reject("unstable under dropping training episodes")
        }

        // Publish the fit on all episodes (hyper-parameters were fixed before looking at the validation set).
        val final = PersonalFitter.fit(examples, composition(examples), params, grid, cfg)
        if (abs(ln(final.timeScale / candidate.timeScale)) > cfg.maxLogTimeScaleShift) return reject("final refit disagrees with the validated fit")
        return TrainOutcome.Accepted(final, report("accepted"))
    }
}
