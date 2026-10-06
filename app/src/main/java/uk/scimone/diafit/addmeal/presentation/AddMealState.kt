package uk.scimone.diafit.addmeal.presentation

import android.net.Uri
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealType

data class AddMealState (
    /** Id of the meal being edited, or null when logging a new one. */
    val editingMealId: Int? = null,
    val imageUri: Uri? = null,
    val description: String? = null,
    val mealTime: Long? = null,
    val carbohydrates: Int? = null,
    val proteins: Int? = null,
    val fats: Int? = null,
    val calories: Int? = null,
    val impactType: ImpactType = ImpactType.MEDIUM,
    /** True while [impactType] is still being suggested from the macros (the user hasn't picked one). */
    val impactAuto: Boolean = true,
    val mealType: MealType = MealType.SNACK,
    /** True while [mealType] still follows the time of day. */
    val mealTypeAuto: Boolean = true,
    val dishName: String? = null,
    val reasoning: String? = null,
    val isAnalyzing: Boolean = false,
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null,
    /** Set once a save/delete finished; the screen consumes it and closes. */
    val finished: EditorResult? = null
) {
    val isEditing: Boolean get() = editingMealId != null

    /** A meal needs at least something to identify it: a photo, a description or carbs. */
    val canSave: Boolean
        get() = imageUri != null || !description.isNullOrBlank() || (carbohydrates ?: 0) > 0

    /** The user-editable fields only, for unsaved-changes detection. */
    fun formFields() = copy(
        isAnalyzing = false, isLoading = false, snackbarMessage = null, finished = null, dishName = null
    )
}

sealed interface EditorResult {
    data class Saved(val wasNew: Boolean) : EditorResult
    data class Deleted(val mealId: Int) : EditorResult
}
