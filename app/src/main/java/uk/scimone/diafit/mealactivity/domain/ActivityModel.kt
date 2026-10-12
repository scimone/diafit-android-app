package uk.scimone.diafit.mealactivity.domain

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * One nutrient's absorption/processing profile for a meal: the cumulative fraction F(t) (0 at t=0, → 1 at the end of the
 * model span) and the rate density f(t) = F'(t) (fraction per minute), both on a 1-minute grid. Gamma-shaped
 * `f(t) ∝ x^(k-1) exp(-x/θ)` with `x = t - onset`; the constant is irrelevant because f is normalised numerically
 * (trapezoid rule) over the span, so ∑ f·Δt = 1 and F never exceeds 1.
 */
internal class Profile(val cumulative: FloatArray, val density: FloatArray) {
    fun fractionAbsorbed(minute: Double): Double = interp(cumulative, minute, 1.0)
    fun densityPerMin(minute: Double): Double = interp(density, minute, 0.0)

    private fun interp(a: FloatArray, minute: Double, afterEnd: Double): Double {
        if (minute <= 0.0) return 0.0
        if (minute >= a.lastIndex) return afterEnd
        val i = floor(minute).toInt()
        val f = minute - i
        return a[i] * (1 - f) + a[i + 1] * f
    }

    companion object {
        fun gamma(onsetMin: Double, shape: Double, scaleMin: Double, spanMin: Int): Profile {
            val d = DoubleArray(spanMin + 1)
            for (t in 0..spanMin) {
                val x = t - onsetMin
                // x^(k-1)·e^(-x/θ) in log-free form is fine: shape and span are bounded; guard overflow anyway.
                d[t] = if (x <= 0.0) 0.0 else exp((shape - 1) * kotlin.math.ln(x) - x / scaleMin)
            }
            var area = 0.0
            for (t in 1..spanMin) area += (d[t - 1] + d[t]) / 2.0
            val cum = FloatArray(spanMin + 1)
            val dens = FloatArray(spanMin + 1)
            if (area <= 0.0 || !area.isFinite()) return Profile(cum, dens) // degenerate: nothing absorbs
            var run = 0.0
            for (t in 1..spanMin) {
                run += (d[t - 1] + d[t]) / 2.0
                cum[t] = min(1.0, run / area).toFloat()
            }
            cum[spanMin] = 1f
            for (t in 0..spanMin) dens[t] = (d[t] / area).toFloat()
            return Profile(cum, dens)
        }
    }
}

/**
 * A meal's estimated nutrient activity over time. **Not a glucose curve**: it describes how much of the meal's
 * carbohydrate/protein/fat is still unprocessed and how fast it is becoming available. Carbohydrate left in the gut is not
 * carbohydrate in the bloodstream. Times are minutes since the meal.
 *
 * Fields (units):
 *  - [carbsRemainingG] (g): quantity not yet absorbed = C·(1 − F_c).
 *  - [carbsRateGph] (g/h): absorption rate = C·f_c·60. **This is what the charts draw.**
 *  - [carbsActiveG] (g-equivalent): effective activity = rate × [ActivityParams.activityWindowHours]; a convention.
 *  - protein/fat remaining (g) and [proteinActivityGph]/[fatActivityGph] (g/h of protein digested / fat processed):
 *    digestion-activity proxies, not glucose and not carbohydrate-equivalents. Null when the macro is unknown.
 */
class MealActivityCurve internal constructor(
    val mealTimeUtc: Long,
    val carbsG: Double,
    private val proteinG: Double?,
    private val fatG: Double?,
    private val carbs: Profile,
    private val protein: Lazy<Profile>,
    private val fat: Lazy<Profile>,
    private val windowHours: Double,
    val lowerConfidenceReasons: List<String>,
    /** Effective carbohydrate timing used (after composition and personal adjustment), for audit. */
    val carbOnsetMin: Double,
    val carbScaleMin: Double
) {
    fun carbsRemainingG(minute: Double): Double = carbsG * (1 - carbs.fractionAbsorbed(minute))
    fun carbsRateGph(minute: Double): Double = carbsG * carbs.densityPerMin(minute) * 60.0
    fun carbsActiveG(minute: Double): Double = carbsRateGph(minute) * windowHours

    fun proteinRemainingG(minute: Double): Double? = proteinG?.let { it * (1 - protein.value.fractionAbsorbed(minute)) }
    fun proteinActivityGph(minute: Double): Double? = proteinG?.let { it * protein.value.densityPerMin(minute) * 60.0 }
    fun fatRemainingG(minute: Double): Double? = fatG?.let { it * (1 - fat.value.fractionAbsorbed(minute)) }
    fun fatActivityGph(minute: Double): Double? = fatG?.let { it * fat.value.densityPerMin(minute) * 60.0 }

    // Absolute-time helpers for charts.
    private fun minutesAt(timeMs: Long) = (timeMs - mealTimeUtc) / 60_000.0
    fun carbsRateGphAt(timeMs: Long): Double = carbsRateGph(minutesAt(timeMs))
    fun carbsRemainingGAt(timeMs: Long): Double = if (timeMs <= mealTimeUtc) carbsG else carbsRemainingG(minutesAt(timeMs))

    /** Minute by which at most [remainingShare] of the carbohydrate is left (default 2 %). Where charts stop drawing. */
    fun carbsEndMinute(remainingShare: Double = 0.02): Double {
        if (carbsG <= 0.0) return 0.0
        val c = carbs.cumulative
        for (t in c.indices) if (1.0 - c[t] <= remainingShare) return t.toDouble()
        return c.lastIndex.toDouble()
    }
    fun carbsEndTimeUtc(remainingShare: Double = 0.02): Long = mealTimeUtc + (carbsEndMinute(remainingShare) * 60_000).toLong()

    /** The curve sampled on [grid], the form that is stored. */
    fun sample(grid: CurveGrid): ActivitySeries {
        fun series(f: (Double) -> Double?): DoubleArray? {
            val out = DoubleArray(grid.size)
            for (k in 0 until grid.size) out[k] = f(grid.minuteAt(k).toDouble()) ?: return null
            return out
        }
        return ActivitySeries(
            grid = grid,
            carbsRemainingG = series { carbsRemainingG(it) }!!,
            carbsRateGph = series { carbsRateGph(it) }!!,
            proteinRemainingG = series { proteinRemainingG(it) },
            proteinActivityGph = series { proteinActivityGph(it) },
            fatRemainingG = series { fatRemainingG(it) },
            fatActivityGph = series { fatActivityGph(it) },
            carbsActiveWindowHours = windowHours
        )
    }
}

/** The stored/served form: values at [CurveGrid] points. `carbs_active_g` is derived, not stored. */
class ActivitySeries(
    val grid: CurveGrid,
    val carbsRemainingG: DoubleArray,
    val carbsRateGph: DoubleArray,
    val proteinRemainingG: DoubleArray?,
    val proteinActivityGph: DoubleArray?,
    val fatRemainingG: DoubleArray?,
    val fatActivityGph: DoubleArray?,
    val carbsActiveWindowHours: Double
) {
    fun carbsActiveG(k: Int): Double = carbsRateGph[k] * carbsActiveWindowHours
    /** Share of the carbohydrate still unabsorbed at the horizon (activity the fixed horizon would otherwise hide). */
    fun carbsResidualShareAtHorizon(carbsG: Double): Double = if (carbsG <= 0.0) 0.0 else carbsRemainingG.last() / carbsG
}

/** The curve generator: baseline model + optional personal adjustment. Immutable, deterministic, cheap to share. */
class MealActivityEngine(
    val params: ActivityParams = ActivityParams(),
    val adjust: PersonalAdjust = PersonalAdjust(),
    val grid: CurveGrid = CurveGrid(),
    private val config: PersonalizationConfig = PersonalizationConfig()
) {
    private fun sat(x: Double?, half: Double) = if (x == null || x <= 0.0) 0.0 else x / (x + half)

    /** Shared slowing effect s in 0..1 of fat, protein and fiber together (diminishing returns when combined). */
    fun slowing(i: MealInput): Double {
        val p = params
        val e = p.fatWeight * sat(i.fatG, p.fatHalfSatG) + p.proteinWeight * sat(i.proteinG, p.proteinHalfSatG) +
            p.fiberWeight * sat(i.fiberG, p.fiberHalfSatG)
        return 1.0 - exp(-e)
    }

    /** Effective (onset, scale) of the carbohydrate absorption for this meal, in minutes. */
    fun carbTiming(i: MealInput): Pair<Double, Double> {
        val p = params
        val s = min(1.0, slowing(i) * adjust.compositionGain.coerceIn(config.compositionGainMin, config.compositionGainMax))
        val speed = when (i.speed) { AbsorptionSpeed.FAST -> p.fastFactor; AbsorptionSpeed.MEDIUM -> p.mediumFactor; AbsorptionSpeed.SLOW -> p.slowFactor }
        val scale = p.carbScaleMin * speed * adjust.timeScale * (1 + p.maxBroaden * s)
        val onset = (p.carbOnsetMin + adjust.onsetShiftMin + p.maxOnsetDelayMin * s).coerceIn(0.0, 120.0)
        return onset to scale
    }

    /** The curve of one course, or null when the input is invalid. */
    fun curve(input: MealInput): MealActivityCurve? {
        val check = input.validate() as? InputCheck.Valid ?: return null
        val p = params
        val (onset, scale) = carbTiming(input)
        val carbs = if ((input.carbsG ?: 0.0) > 0.0) Profile.gamma(onset, p.carbShape, scale, p.modelSpanMinutes) else EMPTY(p.modelSpanMinutes)
        return MealActivityCurve(
            mealTimeUtc = input.mealTimeUtc,
            carbsG = input.carbsG ?: 0.0,
            proteinG = input.proteinG, fatG = input.fatG,
            carbs = carbs,
            protein = lazy { Profile.gamma(p.proteinOnsetMin, p.proteinShape, p.proteinScaleMin, p.modelSpanMinutes) },
            fat = lazy { Profile.gamma(p.fatOnsetMin, p.fatShape, p.fatScaleMin, p.modelSpanMinutes) },
            windowHours = p.activityWindowHours,
            lowerConfidenceReasons = check.lowerConfidenceReasons,
            carbOnsetMin = onset, carbScaleMin = scale
        )
    }

    private fun EMPTY(span: Int) = Profile(FloatArray(span + 1), FloatArray(span + 1))
}
