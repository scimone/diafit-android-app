package uk.scimone.diafit.settings.domain.model

/** How the basal panel draws what the pump delivered. */
enum class BasalStyle(val label: String, val description: String) {
    /** Filled step line of the delivered rate in U/h, scheduled rate dashed. */
    RATE("Rate", "The delivered rate in U/h as a step line, the scheduled rate dashed."),
    /** Bars of the difference to the schedule around a zero line, SMBs as triangles. */
    DEVIATION("Difference", "Bars above/below a zero line show how far the loop moved from the schedule; SMBs are triangles sized by dose.")
}
