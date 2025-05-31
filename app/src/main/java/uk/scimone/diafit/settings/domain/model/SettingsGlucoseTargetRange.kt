package uk.scimone.diafit.settings.domain.model

import uk.scimone.diafit.core.domain.model.GlucoseTargetRange

data class SettingsGlucoseTargetRange(
    val lowerBound: Int = 70,
    val upperBound: Int = 180
)

fun SettingsGlucoseTargetRange.toCore(): GlucoseTargetRange = GlucoseTargetRange(
    lowerBound = this.lowerBound,
    upperBound = this.upperBound
)
