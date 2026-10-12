package uk.scimone.diafit.mealactivity.domain

import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity

/**
 * What the model sees of one course. A macro is `null` when **unknown** (never read as 0).
 *
 * Carbohydrate convention: [carbsG] is the carbohydrate figure as logged (total carbohydrate, or the amount dosed after
 * an AAPS merge) and is treated as the available carbohydrate. Fiber only modifies timing and is **never subtracted**.
 */
data class MealInput(
    val carbsG: Double?,
    val proteinG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
    val speed: AbsorptionSpeed,
    val mealTimeUtc: Long
)

sealed interface InputCheck {
    /** Usable; [lowerConfidenceReasons] is non-empty when optional information is missing. */
    data class Valid(val input: MealInput, val lowerConfidenceReasons: List<String>) : InputCheck
    data class Invalid(val reason: String) : InputCheck
}

private fun Double?.bad(max: Double) = this != null && (!isFinite() || this < 0.0 || this > max)

/**
 * Product policy: carbohydrate is essential (missing/invalid -> no curve). Missing protein, fat or fiber still give a
 * curve, flagged lower-confidence, because their effect is then not applied (and their own series are absent).
 */
fun MealInput.validate(): InputCheck {
    val carbs = carbsG ?: return InputCheck.Invalid("carbohydrate unknown")
    if (carbs.bad(InputLimits.MAX_CARBS_G)) return InputCheck.Invalid("carbohydrate out of range")
    if (proteinG.bad(InputLimits.MAX_PROTEIN_G)) return InputCheck.Invalid("protein out of range")
    if (fatG.bad(InputLimits.MAX_FAT_G)) return InputCheck.Invalid("fat out of range")
    if (fiberG.bad(InputLimits.MAX_FIBER_G)) return InputCheck.Invalid("fiber out of range")
    val reasons = buildList {
        if (proteinG == null) add("protein unknown")
        if (fatG == null) add("fat unknown")
        if (carbs == 0.0) add("no carbohydrate")
    }
    return InputCheck.Valid(this, reasons)
}

fun ImpactType.toSpeed() = when (this) {
    ImpactType.SHORT -> AbsorptionSpeed.FAST
    ImpactType.MEDIUM -> AbsorptionSpeed.MEDIUM
    ImpactType.LONG -> AbsorptionSpeed.SLOW
}

/**
 * Stored meals use nullable Int macros. Imported carb entries (AAPS / Nightscout, `sourceId` set) never carry
 * protein or fat, so a stored 0/0 on such a row means "unknown", not "none". Fiber is known only from the foods list.
 */
fun MealEntity.toMealInput(): MealInput {
    val importedWithoutComposition = sourceId != null && components.isEmpty() && (proteins ?: 0) == 0 && (fats ?: 0) == 0
    return MealInput(
        carbsG = carbohydrates.toDouble(),
        proteinG = if (importedWithoutComposition) null else proteins?.toDouble(),
        fatG = if (importedWithoutComposition) null else fats?.toDouble(),
        fiberG = if (components.isEmpty()) null else components.sumOf { it.fiberG },
        speed = impactType.toSpeed(),
        mealTimeUtc = mealTimeUtc
    )
}
