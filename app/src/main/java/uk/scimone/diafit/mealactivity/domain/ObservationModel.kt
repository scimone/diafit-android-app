package uk.scimone.diafit.mealactivity.domain

import kotlin.math.exp

/** One course of an episode: [offsetMin] after the episode start. */
class CourseSpec(val offsetMin: Int, val input: MealInput)

/**
 * The documented, **fixed** observation model that links the latent carbohydrate absorption to CGM:
 *
 *     Δglucose(t) ≈ G · ∫ a(s) · h(t − s) ds,    h(τ) = (1 − e^(−τ/τ_r)) · e^(−τ/τ_d)
 *
 * with a(s) the carbohydrate absorption rate (g/min), h a fixed net-effect kernel (glucose appears after a short lag,
 * then is removed by insulin action/clearance; τ_r = 15 min, τ_d = 120 min are assumptions, not measurements) and G an
 * unknown **per-episode** gain (mg/dL per g) that soaks up everything amplitude-related: insulin dose and sensitivity,
 * liver output, exercise... Because G is free for each episode, only the *timing/shape* of the absorption profile is
 * calibrated, never its amount. CGM does not measure absorption: this is an inference under the stated assumptions.
 */
object ObservationModel {
    const val RISE_MIN = 15.0
    const val CLEARANCE_MIN = 120.0
    private val kernel = DoubleArray(1441) { tau -> (1 - exp(-tau / RISE_MIN)) * exp(-tau / CLEARANCE_MIN) }

    /**
     * Unit-gain modelled glucose change (mg/dL per unit G) at the grid points for the whole episode. Index 0 is 0 by
     * construction. Uses 1-minute absorption rates per course.
     */
    fun shape(courses: List<CourseSpec>, engine: MealActivityEngine): DoubleArray {
        val grid = engine.grid
        val out = DoubleArray(grid.size)
        for (c in courses) {
            val curve = engine.curve(c.input) ?: continue
            val maxU = grid.horizonMinutes - c.offsetMin
            if (maxU <= 0) continue
            val a = DoubleArray(maxU + 1) { curve.carbsRateGph(it + 0.5) / 60.0 } // g/min over minute [u, u+1)
            for (k in 1 until grid.size) {
                val tRel = grid.minuteAt(k) - c.offsetMin
                if (tRel <= 0) continue
                var sum = 0.0
                for (u in 0 until tRel) sum += a[u] * kernel[tRel - u]
                out[k] += sum
            }
        }
        return out
    }
}
