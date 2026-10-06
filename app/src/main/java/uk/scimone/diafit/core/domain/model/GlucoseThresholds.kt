package uk.scimone.diafit.core.domain.model


/**
 * Glucose zones used to layer a horizon chart: [low]..[high] is the user's target, the "very"
 * limits are fixed consensus values, and [FLOOR]/[CEILING] are where the outer bands saturate.
 */
data class GlucoseThresholds(
    val low: Int,
    val high: Int,
    val veryLow: Int = VERY_LOW,
    val veryHigh: Int = VERY_HIGH
) {
    /** How deep into each out-of-range band [mgdl] reaches, 0 (not at all) to 1 (saturated). */
    fun levels(mgdl: Int): BandLevels = BandLevels(
        high = fraction(mgdl - high, veryHigh - high),
        veryHigh = fraction(mgdl - veryHigh, CEILING - veryHigh),
        low = fraction(low - mgdl, low - veryLow),
        veryLow = fraction(veryLow - mgdl, veryLow - FLOOR)
    )

    private fun fraction(part: Int, whole: Int): Float =
        if (whole <= 0) 0f else (part.toFloat() / whole).coerceIn(0f, 1f)

    companion object {
        const val VERY_LOW = 54
        const val VERY_HIGH = 250
        const val FLOOR = 40
        const val CEILING = 350

        fun from(target: GlucoseTargetRange) = GlucoseThresholds(target.lowerBound, target.upperBound)
    }
}

data class BandLevels(val high: Float, val veryHigh: Float, val low: Float, val veryLow: Float)
