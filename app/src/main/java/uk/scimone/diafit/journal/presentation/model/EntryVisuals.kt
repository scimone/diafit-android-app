package uk.scimone.diafit.journal.presentation.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import uk.scimone.diafit.R
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.ui.theme.Activity
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs

/**
 * The kinds of entry the journal can hold. Only [MEAL] is implemented; the others exist so the
 * "new entry" chooser, the filter row and the list can already treat entries generically.
 * To add a kind: add an editor + detail renderer for it and flip [available].
 */
enum class JournalEntryKind(
    val label: String,
    val pluralLabel: String,
    val accent: Color,
    val available: Boolean,
    /** Offered in the "new entry" chooser; false for entries derived from data (lows/highs). */
    val creatable: Boolean = true
) {
    MEAL("Meal", "Meals", Carbs, available = true),
    GLUCOSE("Low or high", "Lows & highs", BelowRange, available = true, creatable = false),
    BOLUS("Insulin", "Insulin", Bolus, available = true, creatable = false),
    SLEEP("Sleep", "Sleep", Color(0xFF7986CB), available = false),
    ACTIVITY("Activity", "Activity", Activity, available = false);

    companion object {
        val availableKinds: List<JournalEntryKind> get() = entries.filter { it.available }
        val creatableKinds: List<JournalEntryKind> get() = entries.filter { it.creatable }
    }
}

/** What every journal row needs to know about an entry, whatever its kind. */
sealed interface JournalEntryUi {
    val id: Int
    val kind: JournalEntryKind
    val timeUtc: Long
}

/** Per-meal-type accent for tonal avatar tiles. */
val MealType.accent: Color
    get() = when (this) {
        MealType.BREAKFAST -> Color(0xFFFFB74D)
        MealType.LUNCH -> Color(0xFF4DB6AC)
        MealType.DINNER -> Color(0xFF7986CB)
        MealType.SNACK -> Color(0xFFFF8A65)
    }

@get:DrawableRes
val MealType.iconRes: Int
    get() = when (this) {
        MealType.BREAKFAST -> R.drawable.ic_meal_type_breakfast
        MealType.LUNCH -> R.drawable.ic_meal_type_lunch
        MealType.DINNER -> R.drawable.ic_meal_type_dinner
        MealType.SNACK -> R.drawable.ic_meal_type_snack
    }

@get:DrawableRes
val ImpactType.iconRes: Int
    get() = when (this) {
        ImpactType.SHORT -> R.drawable.ic_absorption_fast
        ImpactType.MEDIUM -> R.drawable.ic_absorption_medium
        ImpactType.LONG -> R.drawable.ic_absorption_slow
    }

val ImpactType.label: String
    get() = when (this) {
        ImpactType.SHORT -> "Fast"
        ImpactType.MEDIUM -> "Medium"
        ImpactType.LONG -> "Slow"
    }

/** "~2 h" style duration of the carb absorption window. */
val ImpactType.durationLabel: String
    get() = "~${durationMinutes / 60} h"

val ImpactType.hint: String
    get() = when (this) {
        ImpactType.SHORT -> "Sugary or low-fat food. Glucose rises quickly and peaks early."
        ImpactType.MEDIUM -> "Typical mixed meal. Glucose rises steadily over a few hours."
        ImpactType.LONG -> "High in fat and protein. A slow, extended rise, often delayed."
    }
