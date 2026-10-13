package uk.scimone.diafit.dailyrecap.domain

import kotlinx.serialization.Serializable

/** One line of the recap; [timeUtc] is the moment it refers to (drawn on the chart), null when it isn't tied to one. */
@Serializable
data class RecapPoint(val text: String, val timeUtc: Long? = null)

/** The finished recap of one day: the LLM's words plus the numbers the chart is drawn from (never taken from the LLM). */
@Serializable
data class DailyRecap(
    val epochDay: Long,
    val generatedAtUtc: Long,
    /** The day was still running when this was written (a test run, or the phone was off at 07:00). */
    val partialDay: Boolean,
    val headline: String,
    /** "good", "mixed" or "hard": colours the headline. */
    val mood: String,
    val wentWell: List<RecapPoint>,
    val wentWrong: List<RecapPoint>,
    val doToday: List<String>,
    val visual: RecapVisual
)

@Serializable data class VisualPoint(val t: Long, val g: Int)
@Serializable data class VisualMeal(val t: Long, val carbsG: Int)
@Serializable data class VisualBolus(val t: Long, val units: Float)
@Serializable data class VisualSpan(val from: Long, val to: Long, val label: String)

/** Everything the recap chart shows, kept with the recap so the page and the notification draw the same picture. */
@Serializable
data class RecapVisual(
    val dayStartUtc: Long,
    val dayEndUtc: Long,
    /** Where the data ends: the day end, or "now" for a day still running. */
    val shownUntilUtc: Long,
    val lowMgdl: Int,
    val highMgdl: Int,
    /** Readings thinned to one per 10 minutes. */
    val points: List<VisualPoint>,
    val meals: List<VisualMeal>,
    val boluses: List<VisualBolus>,
    val workouts: List<VisualSpan>,
    /** Time-weighted shares 0..1: very low, low, in range, high, very high. */
    val zoneShares: List<Float>,
    /** The same day-share averaged over the seven days before, for the "vs usual" chip; null without enough data. */
    val baselineInRange: Float? = null
) {
    val inRange: Float get() = zoneShares.getOrElse(2) { 0f }
}
