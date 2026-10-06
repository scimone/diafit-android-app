package uk.scimone.diafit.addmeal.presentation

import android.net.Uri
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealComponent
import uk.scimone.diafit.core.domain.model.totals
import uk.scimone.diafit.core.domain.model.MealPhoto
import uk.scimone.diafit.core.domain.model.MealType

/** Most photos one course can hold (also bounds the size of one AI request). */
const val MAX_PHOTOS_PER_COURSE = 8

data class AddMealState(
    /** Id of the course being edited, or null when logging a new one. */
    val editingMealId: Int? = null,
    /** Photos of this course, cover first. */
    val photos: List<MealPhoto> = emptyList(),
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
    /** The meal this course belongs to, when it has (or will have) other courses. */
    val sitting: SittingContext? = null,
    val dishName: String? = null,
    /** Photo ids the last AI analysis looked at; differs from [photos] once photos are added/removed. */
    val analyzedPhotoIds: List<String>? = null,
    val reasoning: String? = null,
    /** Optional context sent to the AI together with the photos, e.g. "I only ate half". */
    val aiNotes: String = "",
    /** Foods identified in the photos; the nutrition totals start as their sum (the user may still override them). */
    val components: List<MealComponent> = emptyList(),
    val isAnalyzing: Boolean = false,
    val isLoading: Boolean = false,
    val snackbarMessage: String? = null,
    /** Set once a save/delete finished; the screen consumes it and closes. */
    val finished: EditorResult? = null
) {
    /** Set when the typed totals differ from the sum of the components (the manual value wins). */
    val componentCarbsHint: Int?
        get() = components.takeIf { it.isNotEmpty() }?.totals()?.carbs?.takeIf { it != carbohydrates }

    val isEditing: Boolean get() = editingMealId != null

    /** Logging a new course into an existing meal. */
    val isAddingCourse: Boolean get() = !isEditing && sitting != null

    val canAddPhoto: Boolean get() = photos.size < MAX_PHOTOS_PER_COURSE

    /** Photos changed since the last AI estimate, so it may no longer match. */
    val analysisOutdated: Boolean
        get() = analyzedPhotoIds != null && analyzedPhotoIds != photos.map { it.imageId }

    /** A course needs at least something to identify it: a photo, a description or carbs. */
    val canSave: Boolean
        get() = photos.isNotEmpty() || !description.isNullOrBlank() || (carbohydrates ?: 0) > 0

    /** The user-editable fields only, for unsaved-changes detection. */
    fun formFields() = copy(
        isAnalyzing = false, isLoading = false, snackbarMessage = null, finished = null, dishName = null,
        analyzedPhotoIds = null, sitting = null, aiNotes = ""
    )
}

/**
 * The meal a course belongs to, shown above the form so the user doses for this course knowing
 * what has already been eaten and injected.
 */
data class SittingContext(
    val title: String,
    val startTime: Long,
    /** The meal's *other* courses, oldest first. */
    val otherCourses: List<CourseSummary>,
    /** Insulin delivered since the meal started. */
    val insulinUnits: Double
) {
    val otherCarbs: Int get() = otherCourses.sumOf { it.carbs }
}

data class CourseSummary(val id: Int, val time: Long, val carbs: Int, val title: String, val photo: Uri?)

sealed interface EditorResult {
    data class Saved(val wasNew: Boolean, val addedCourse: Boolean = false) : EditorResult
    data class Deleted(val mealId: Int) : EditorResult
}
