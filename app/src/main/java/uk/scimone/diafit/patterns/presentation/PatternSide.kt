package uk.scimone.diafit.patterns.presentation

import androidx.compose.ui.graphics.Color
import uk.scimone.diafit.patterns.domain.PatternCard
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange

/** Which side of the target range a pattern is about; such patterns take the colour of low / high glucose. */
enum class GlucoseSide(val color: Color) { LOW(BelowRange), HIGH(AboveRange) }

/**
 * Derived from the detector (and, where one detector can go either way, the key of the title message), so patterns stored before this
 * existed are coloured too. Null = not about low or high glucose (steadiness, trends in range, sleep...).
 */
fun PatternCard.glucoseSide(): GlucoseSide? = when (detector) {
    "low", "meal-low", "ex-low", "ex-delayed", "ex-drop", "ex-long", "ex-evening", "combo-eveninglow", "trend-lows" -> GlucoseSide.LOW
    "high", "meal-spike", "meal-slow", "meal-rich", "meal-type", "meal-bolus", "ex-rise", "dawn", "combo-latedinner", "combo-shortbreakfast", "ctx-mealdelay", "ctx-latedinnerrich" -> GlucoseSide.HIGH
    "trend-mean" -> if (title.k == "pat_trend_mean_up") GlucoseSide.HIGH else null
    "ex-intensity" -> when (title.k) { "pat_ex_int_title_down" -> GlucoseSide.LOW; "pat_ex_int_title_up" -> GlucoseSide.HIGH; else -> null }
    "ctx-expre" -> when (title.k) { "pat_ctx_expre_title_fall" -> GlucoseSide.LOW; "pat_ctx_expre_title_rise" -> GlucoseSide.HIGH; else -> null }
    "ctx-extype" -> when (title.k) { "pat_ctx_extype_title_low" -> GlucoseSide.LOW; "pat_ctx_extype_title_high" -> GlucoseSide.HIGH; else -> null }
    "ctx-exgap" -> GlucoseSide.LOW
    "ctx-mealtime", "ctx-mealweekend", "ctx-mealactivity" ->
        if (title.k in setOf("pat_ctx_time_title_worse", "pat_ctx_we_title_worse", "pat_ctx_act_title_worse")) GlucoseSide.HIGH else null
    else -> null
}
