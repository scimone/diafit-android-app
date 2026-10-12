package uk.scimone.diafit.mealactivity.domain

import kotlinx.serialization.Serializable

/**
 * Versions of every moving part. Stored curves and personal models carry the versions they were made with and the
 * pipeline recomputes whatever no longer matches [algoKey]. Bump the matching constant when behaviour changes.
 */
object ActivityVersions {
    /** The absorption model and its default [ActivityParams]. */
    const val ALGORITHM = 1
    /** Personalization method ([PersonalFitter], [ObservationModel]) and [PersonalizationConfig]. */
    const val PERSONALIZATION = 1
    /** Training-episode construction ([TrainingRules], [TrainingExampleBuilder]). */
    const val FEATURES = 1

    fun algoKey(grid: CurveGrid = CurveGrid()) =
        "a$ALGORITHM-p$PERSONALIZATION-f$FEATURES-h${grid.horizonMinutes}-s${grid.stepMinutes}"
}

/**
 * Sampling grid of stored/served curves: points at 0, step, … horizon minutes after the meal (elapsed real time, so
 * daylight-saving changes cannot shift it). Absorption itself is modelled out to [ActivityParams.modelSpanMinutes];
 * the horizon is only where a curve is *sampled*, so activity still remaining at the horizon is reported, not cut off.
 */
@Serializable
data class CurveGrid(val horizonMinutes: Int = 360, val stepMinutes: Int = 15) {
    init { require(horizonMinutes > 0 && stepMinutes > 0 && horizonMinutes % stepMinutes == 0) }
    val size: Int get() = horizonMinutes / stepMinutes + 1
    fun minuteAt(k: Int): Int = k * stepMinutes
}

/** Input validation limits (grams); values beyond them are rejected, not clamped. */
object InputLimits {
    const val MAX_CARBS_G = 500.0
    const val MAX_PROTEIN_G = 300.0
    const val MAX_FAT_G = 300.0
    const val MAX_FIBER_G = 100.0
    /** Smaller carbohydrate amounts are too noisy to be training episodes. */
    const val MIN_TRAINING_CARBS_G = 10.0
}

/** How fast the carbohydrate itself is absorbed (the user's/AI's "absorption" choice on the meal). */
enum class AbsorptionSpeed { FAST, MEDIUM, SLOW }

/**
 * Baseline parameters. **Engineering defaults, not physiological constants**: nothing here was fitted on a
 * representative dataset, there is no validated "minutes of delay per gram of fat", and an individual can differ
 * a lot. Every figure is configurable and versioned ([ActivityVersions.ALGORITHM]); personalization corrects timing.
 *
 * Carbohydrate: gamma-shaped absorption-rate density f_c with onset, shape k and scale θ. Cumulative F_c(t) = ∫ f_c,
 * normalised so F_c(∞ ≈ modelSpan) = 1; carbs_absorbed(t) = C·F_c(t), carbs_remaining(t) = C·(1 − F_c(t)).
 */
@Serializable
data class ActivityParams(
    val carbOnsetMin: Double = 5.0,
    val carbShape: Double = 2.5,
    val carbScaleMin: Double = 30.0,        // mode = onset + (k-1)θ = 50 min; mean = onset + kθ = 80 min

    /** Multipliers of θ for the carbohydrate source/absorption choice on the meal. */
    val fastFactor: Double = 0.75, val mediumFactor: Double = 1.0, val slowFactor: Double = 1.3,

    /**
     * Mixed-meal slowing. A single saturating, shared effect `s = 1 - exp(-(wF·sat(fat) + wP·sat(protein) + wFi·sat(fiber)))`
     * (so fat, protein and fiber together slow less than the sum of their separate effects) broadens the carbohydrate
     * curve (θ × (1 + maxBroaden·s)) and delays its onset (+ maxOnsetDelay·s). Unknown macros contribute nothing.
     */
    val fatWeight: Double = 1.2, val fatHalfSatG: Double = 40.0,
    val proteinWeight: Double = 0.5, val proteinHalfSatG: Double = 40.0,
    val fiberWeight: Double = 0.4, val fiberHalfSatG: Double = 8.0,
    val maxBroaden: Double = 1.0,
    val maxOnsetDelayMin: Double = 20.0,

    /** Protein digestion (amino-acid appearance proxy) and fat processing (digestive) timing: separate, slower curves. */
    val proteinOnsetMin: Double = 30.0, val proteinShape: Double = 3.0, val proteinScaleMin: Double = 70.0,
    val fatOnsetMin: Double = 45.0, val fatShape: Double = 3.0, val fatScaleMin: Double = 90.0,

    /**
     * Convention for `carbs_active_g`: active = absorption rate (g/h) × this many hours. A modelling convention that
     * turns a rate into a gram-equivalent; it is NOT a measured pool of glucose in the body.
     */
    val activityWindowHours: Double = 1.0,

    /** Absorption is integrated and normalised over this span (complete absorption is assumed within it). */
    val modelSpanMinutes: Int = 1440
)

/** Individual corrections to the baseline timing, learned by [PersonalFitter]; all neutral = the baseline. */
@Serializable
data class PersonalAdjust(
    /** Stretches (>1) or compresses (<1) the carbohydrate absorption width/peak time after onset. */
    val timeScale: Double = 1.0,
    /** Minutes added to the carbohydrate onset. */
    val onsetShiftMin: Double = 0.0,
    /** Scales the fat/protein/fiber slowing effect. */
    val compositionGain: Double = 1.0
)

@Serializable
data class PersonalizationConfig(
    val minExamples: Int = 30,
    val minHoldout: Int = 8,
    val holdoutFraction: Double = 0.25,
    /** Carbohydrate spread (CV) needed across episodes. */
    val minCarbCv: Double = 0.2,
    /** The composition gain is only estimated with this many episodes of known fat+protein and a real spread; else frozen at 1. */
    val minCompositionEpisodes: Int = 15,
    val minCompositionCv: Double = 0.3,

    val timeScaleMin: Double = 0.6, val timeScaleMax: Double = 1.8,
    val onsetShiftMinMin: Double = -5.0, val onsetShiftMaxMin: Double = 30.0,
    val compositionGainMin: Double = 0.0, val compositionGainMax: Double = 2.5,

    /** Prior toward the baseline, worth this many episodes at one sigma. */
    val priorEpisodes: Double = 4.0,
    val sigmaLogTimeScale: Double = 0.3, val sigmaOnsetMin: Double = 10.0, val sigmaCompositionGain: Double = 1.0,
    val huberDeltaMgdl: Double = 40.0,

    // Promotion criteria (candidate vs baseline timing on the same time-ordered held-out episodes).
    val requiredMaeRatio: Double = 0.95,
    val maxRmseRatio: Double = 1.0,
    val maxPeakTimeWorseMin: Double = 15.0,
    // Stability when every 5th training episode is dropped.
    val maxLogTimeScaleShift: Double = 0.25, val maxOnsetShiftMin: Double = 12.0, val maxCompositionShift: Double = 1.0
)

/** Rules for turning a meal + CGM history into a supervised episode. */
@Serializable
data class TrainingRules(
    val baselineWindowMin: Int = 15,
    val maxBaselineGapMin: Int = 10,
    val maxBaselineRangeMgdl: Double = 30.0,
    val minBaselineMgdl: Double = 70.0,
    /** A meal (>= [InputLimits.MIN_TRAINING_CARBS_G]) this recent before the start confounds the baseline. */
    val priorMealExclusionMin: Int = 120,
    /** The clean span (until the next meal or the horizon) must be at least this long. */
    val minCleanSpanMin: Int = 180,
    val minCoverage: Double = 0.7,
    val maxConsecutiveMissingBins: Int = 2,
    val minValidMgdl: Int = 20, val maxValidMgdl: Int = 600,
    val windowDays: Int = 180,
    val maxEpisodes: Int = 300
)
