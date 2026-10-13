package uk.scimone.diafit.settings.domain.model

import androidx.annotation.StringRes
import uk.scimone.diafit.R

/** How the basal panel draws what the pump delivered. */
enum class BasalStyle(@StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    /** Filled step line of the delivered rate in U/h, scheduled rate dashed. */
    RATE(R.string.basal_style_rate, R.string.basal_style_rate_desc),
    /** Bars of the difference to the schedule around a zero line, SMBs as triangles. */
    DEVIATION(R.string.basal_style_deviation, R.string.basal_style_deviation_desc)
}
