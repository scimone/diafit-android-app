package uk.scimone.diafit.logs.presentation.model

import uk.scimone.diafit.core.presentation.AppStrings
import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import uk.scimone.diafit.R
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.ui.theme.Activity
import uk.scimone.diafit.ui.theme.BelowRange
import uk.scimone.diafit.ui.theme.Bolus
import uk.scimone.diafit.ui.theme.Carbs
import uk.scimone.diafit.ui.theme.Device
import uk.scimone.diafit.ui.theme.Sleep

/**
 * The kinds of entry the logs can hold. Only [MEAL] is implemented; the others exist so the
 * "new entry" chooser, the filter row and the list can already treat entries generically.
 * To add a kind: add an editor + detail renderer for it and flip [available].
 */
enum class LogsEntryKind(
    @StringRes val labelResId: Int,
    @StringRes private val pluralRes: Int,
    @StringRes val subtitleRes: Int,
    @StringRes val emptyRes: Int,
    private val accentOf: () -> Color,
    val available: Boolean,
    /** Offered in the "new entry" chooser; false for entries derived from data (lows/highs). */
    val creatable: Boolean = true
) {
    MEAL(R.string.logs_kind_meal, R.string.logs_kind_meal_plural, R.string.logs_kind_meal_sub, R.string.logs_empty_meal, { Carbs }, available = true),
    GLUCOSE(R.string.logs_kind_glucose, R.string.logs_kind_glucose_plural, R.string.logs_kind_glucose_sub, R.string.logs_empty_glucose, { BelowRange }, available = true, creatable = false),
    BOLUS(R.string.logs_kind_bolus, R.string.logs_kind_bolus, R.string.logs_kind_bolus_sub, R.string.logs_empty_bolus, { Bolus }, available = true, creatable = false),
    DEVICE(R.string.logs_kind_device, R.string.logs_kind_device_plural, R.string.logs_kind_device_sub, R.string.logs_empty_device, { Device }, available = true, creatable = false),
    SLEEP(R.string.logs_kind_sleep, R.string.logs_kind_sleep, R.string.logs_kind_sleep_sub, R.string.logs_empty_sleep, { Sleep }, available = true, creatable = false),
    ACTIVITY(R.string.logs_kind_activity, R.string.logs_kind_activity, R.string.logs_kind_activity_sub, R.string.logs_empty_activity, { Activity }, available = true, creatable = false);

    val label: String get() = AppStrings.get(labelResId)
    val pluralLabel: String get() = AppStrings.get(pluralRes)

    /** Read live so it follows the light/dark palette. */
    val accent: Color get() = accentOf()

    companion object {
        val availableKinds: List<LogsEntryKind> get() = entries.filter { it.available }
        val creatableKinds: List<LogsEntryKind> get() = entries.filter { it.creatable }
    }
}

/** What every logs row needs to know about an entry, whatever its kind. */
sealed interface LogsEntryUi {
    val id: Int
    val kind: LogsEntryKind
    val timeUtc: Long
}

/** Per-meal-type accent for tonal avatar tiles. */
val MealType.accent: Color
    get() = when (this) {
        MealType.BREAKFAST -> uk.scimone.diafit.ui.theme.MealBreakfast
        MealType.LUNCH -> uk.scimone.diafit.ui.theme.MealLunch
        MealType.DINNER -> uk.scimone.diafit.ui.theme.MealDinner
        MealType.SNACK -> uk.scimone.diafit.ui.theme.MealSnack
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

@get:StringRes
val ImpactType.labelRes: Int
    get() = when (this) {
        ImpactType.SHORT -> R.string.absorption_fast
        ImpactType.MEDIUM -> R.string.absorption_medium
        ImpactType.LONG -> R.string.absorption_slow
    }

val ImpactType.label: String get() = AppStrings.get(labelRes)

/** "~2 h" style duration of the carb absorption window. */
val ImpactType.durationLabel: String
    get() = "~${durationMinutes / 60} h"

@get:StringRes
val ImpactType.hintRes: Int
    get() = when (this) {
        ImpactType.SHORT -> R.string.absorption_fast_hint
        ImpactType.MEDIUM -> R.string.absorption_medium_hint
        ImpactType.LONG -> R.string.absorption_slow_hint
    }

val ImpactType.hint: String get() = AppStrings.get(hintRes)
