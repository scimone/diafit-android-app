package uk.scimone.diafit.core.domain.model

import kotlin.math.ceil
import kotlin.math.exp

/** Gaussian smoothing applied to AGP curves (sigma, in minutes), both for display and for pattern detection. */
const val AGP_SMOOTHING_MINUTES = 25.0

/**
 * Circular Gaussian blur of a time-of-day curve (the day wraps at midnight), sigma in minutes. NaN bins stay NaN
 * and don't pull their neighbours.
 */
fun DoubleArray.gaussianSmoothCircular(sigmaMinutes: Double = AGP_SMOOTHING_MINUTES): DoubleArray {
    val sigma = sigmaMinutes / (24 * 60.0 / size)
    val radius = ceil(3 * sigma).toInt()
    val kernel = DoubleArray(2 * radius + 1) { val d = it - radius; exp(-d * d / (2 * sigma * sigma)) }
    return DoubleArray(size) { i ->
        if (this[i].isNaN()) Double.NaN else {
            var sum = 0.0
            var weight = 0.0
            for (k in kernel.indices) {
                val v = this[Math.floorMod(i + k - radius, size)]
                if (!v.isNaN()) { sum += v * kernel[k]; weight += kernel[k] }
            }
            sum / weight
        }
    }
}
