package uk.scimone.diafit.patterns.presentation

import androidx.compose.ui.graphics.Color
import uk.scimone.diafit.patterns.domain.PatternCard
import uk.scimone.diafit.ui.theme.AboveRange
import uk.scimone.diafit.ui.theme.BelowRange

/** Which side of the target range a pattern is about; such patterns take the colour of low / high glucose. */
enum class GlucoseSide(val color: Color) { LOW(BelowRange), HIGH(AboveRange) }

/**
 * Derived from the detector (and, where one detector can go either way, the title), so patterns stored before this
 * existed are coloured too. Null = not about low or high glucose (steadiness, trends in range, sleep...).
 */
fun PatternCard.glucoseSide(): GlucoseSide? = when (detector) {
    "low", "meal-low", "ex-low", "ex-delayed", "ex-drop", "ex-long", "ex-evening", "combo-eveninglow", "trend-lows" -> GlucoseSide.LOW
    "high", "meal-spike", "meal-slow", "meal-rich", "meal-type", "meal-bolus", "ex-rise", "dawn", "combo-latedinner", "combo-shortbreakfast", "ctx-mealdelay", "ctx-latedinnerrich" -> GlucoseSide.HIGH
    "trend-mean" -> if (title.startsWith("Higher")) GlucoseSide.HIGH else null
    "ex-intensity", "ctx-expre", "ctx-extype", "ctx-exgap" ->
        if (title.contains("drop") || title.startsWith("Lower")) GlucoseSide.LOW
        else if (title.contains("rise") || title.startsWith("Higher")) GlucoseSide.HIGH else null
    "ctx-mealtime", "ctx-mealweekend", "ctx-mealactivity" -> if (title.startsWith("Bigger")) GlucoseSide.HIGH else null
    else -> null
}
