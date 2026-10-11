package uk.scimone.diafit.core.domain.model

data class GlucoseTargetRange(
    val lowerBound: Int,
    val upperBound: Int,
    /** Below / above these the glucose counts as very low / very high. */
    val veryLowBound: Int = GlucoseThresholds.VERY_LOW,
    val veryHighBound: Int = GlucoseThresholds.VERY_HIGH
)
