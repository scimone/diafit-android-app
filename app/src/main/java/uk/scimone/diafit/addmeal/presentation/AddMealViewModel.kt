package uk.scimone.diafit.addmeal.presentation

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealComponent
import uk.scimone.diafit.core.domain.model.totals
import uk.scimone.diafit.core.domain.model.withMacros
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealEntity.Companion.inferImpactType
import uk.scimone.diafit.core.domain.model.MealEntity.Companion.inferMealType
import uk.scimone.diafit.core.domain.model.MealPhoto
import uk.scimone.diafit.core.domain.model.MealSitting
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.core.domain.repository.BolusRepository
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.usecase.AnalyzeMealUseCase
import uk.scimone.diafit.core.domain.usecase.CreateMealUseCase
import uk.scimone.diafit.core.domain.usecase.GetMealSittingUseCase
import uk.scimone.diafit.core.domain.usecase.SetMealValidUseCase
import uk.scimone.diafit.core.domain.usecase.UpdateMealUseCase
import uk.scimone.diafit.core.domain.util.localDateTimeToInstant
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

private const val TAG = "AddMealViewModel"

/** A course added this long after the meal's last one defaults to "now"; older meals get last + this. */
private const val COURSE_DEFAULT_GAP_MS = 10 * 60_000L
private const val RECENT_MEAL_MS = 3 * 60 * 60_000L

/**
 * Drives the meal editor: logging a new meal, adding a course to an existing meal, and editing a course.
 */
class AddMealViewModel(
    private val createMealUseCase: CreateMealUseCase,
    private val updateMealUseCase: UpdateMealUseCase,
    private val setMealValidUseCase: SetMealValidUseCase,
    private val analyzeMealUseCase: AnalyzeMealUseCase,
    private val getMealSitting: GetMealSittingUseCase,
    private val mealRepository: MealRepository,  // TODO: Use usecase instead of repository
    private val bolusRepository: BolusRepository,
    private val fileStorageRepository: FileStorageRepository,  // TODO: Use usecase instead of repository
    private val userId: Int,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AddMealState())
    val uiState = _uiState.asStateFlow()

    /** Photo files created in this session and not saved yet; deleted if the editor is left without saving. */
    private val unsavedPhotoIds = mutableSetOf<String>()
    private var pendingCamera: MealPhoto? = null
    private var editing: MealEntity? = null
    /** The meal a new course joins (its courses), or null for a brand-new meal. */
    private var targetSitting: MealSitting? = null
    private var baseline: AddMealState = AddMealState()
    /** The meal's title when the editor opened, to tell a rename from an untouched default. */
    private var sittingTitleAtStart: String? = null

    private fun reset() {
        editing = null
        targetSitting = null
        sittingTitleAtStart = null
        pendingCamera = null
        unsavedPhotoIds.clear()
    }

    fun startNewMeal() {
        reset()
        val now = LocalDateTime.now()
        _uiState.value = AddMealState(
            mealTime = localDateTimeToInstant(now).toEpochMilli(),
            mealType = inferMealType(now.hour),
            description = ""
        )
        baseline = _uiState.value.formFields()
    }

    /** A new course for the meal that [mealId] belongs to. */
    fun startAddingCourse(mealId: Int) {
        reset()
        viewModelScope.launch {
            val sitting = getMealSitting(mealId)
            if (sitting == null) {
                startNewMeal()
                return@launch
            }
            targetSitting = sitting
            sittingTitleAtStart = sitting.title
            val now = System.currentTimeMillis()
            val time = if (now - sitting.endTime < RECENT_MEAL_MS) now else sitting.endTime + COURSE_DEFAULT_GAP_MS
            _uiState.value = AddMealState(
                mealTime = time,
                mealType = sitting.courses.first().mealType,
                mealTypeAuto = false,
                description = "",
                sitting = sittingContext(sitting, excludeId = null),
                mealName = sitting.title
            )
            baseline = _uiState.value.formFields()
        }
    }

    fun startEditing(mealId: Int) {
        reset()
        viewModelScope.launch {
            val meal = mealRepository.getMealById(mealId)
            if (meal == null) {
                _uiState.update { it.copy(snackbarMessage = "This meal no longer exists", finished = EditorResult.Deleted(mealId)) }
                return@launch
            }
            editing = meal
            val sitting = getMealSitting(mealId)?.takeIf { it.isExtended }
            sittingTitleAtStart = sitting?.title
            _uiState.value = AddMealState(
                editingMealId = meal.id,
                photos = meal.photoIds.mapNotNull { id -> fileStorageRepository.getFileProviderUri(id)?.let { MealPhoto(id, it) } },
                description = meal.description.orEmpty(),
                mealTime = meal.mealTimeUtc,
                carbohydrates = meal.carbohydrates,
                proteins = meal.proteins,
                fats = meal.fats,
                calories = meal.calories,
                impactType = meal.impactType,
                impactAuto = false,
                mealType = meal.mealType,
                mealTypeAuto = false,
                reasoning = meal.reasoning,
                components = meal.components,
                sitting = sitting?.let { sittingContext(it, excludeId = meal.id) },
                mealName = sitting?.title.orEmpty()
            )
            baseline = _uiState.value.formFields()
        }
    }

    private suspend fun sittingContext(sitting: MealSitting, excludeId: Int?): SittingContext {
        val insulin = runCatching {
            bolusRepository.getBolusBetween(sitting.startTime - 30 * 60_000L, System.currentTimeMillis(), userId).sumOf { it.value.toDouble() }
        }.onFailure { Log.e(TAG, "Couldn't load insulin for the meal", it) }.getOrDefault(0.0)
        return SittingContext(
            title = sitting.title,
            startTime = sitting.startTime,
            otherCourses = sitting.courses.filter { it.id != excludeId }.mapIndexed { i, c ->
                CourseSummary(
                    id = c.id,
                    time = c.mealTimeUtc,
                    carbs = c.carbohydrates,
                    title = c.description?.takeIf { it.isNotBlank() } ?: "Course ${i + 1}",
                    photo = c.photoIds.firstOrNull()?.let { fileStorageRepository.getFileProviderUri(it) }
                )
            },
            insulinUnits = insulin
        )
    }

    fun isDirty(): Boolean = _uiState.value.formFields() != baseline

    /** Called when the editor is left without saving. */
    fun discard() {
        val orphans = unsavedPhotoIds.toList()
        unsavedPhotoIds.clear()
        if (orphans.isNotEmpty()) viewModelScope.launch { orphans.forEach { fileStorageRepository.deleteImage(it) } }
    }

    fun resetSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /** Uri for the camera to write the next photo into; report back with [onCameraResult]. */
    fun createCameraImageUri(): Uri {
        val id = UUID.randomUUID().toString()
        val uri = fileStorageRepository.createImageUri(id)
        pendingCamera = MealPhoto(id, uri)
        unsavedPhotoIds += id
        return uri
    }

    fun onCameraResult(success: Boolean) {
        val photo = pendingCamera ?: return
        pendingCamera = null
        if (success) addPhotos(listOf(photo))
        else {
            unsavedPhotoIds -= photo.imageId
            viewModelScope.launch { fileStorageRepository.deleteImage(photo.imageId) }
        }
    }

    /** Copies picked gallery images into private storage and adds them, up to [MAX_PHOTOS_PER_COURSE]. */
    fun onGalleryImagesPicked(sourceUris: List<Uri>) {
        val room = MAX_PHOTOS_PER_COURSE - _uiState.value.photos.size
        if (sourceUris.isEmpty() || room <= 0) return
        viewModelScope.launch {
            val copied = sourceUris.take(room).mapNotNull { source ->
                val id = UUID.randomUUID().toString()
                fileStorageRepository.copyGalleryImageToPrivateStorage(source, id)
                    .onFailure { Log.e(TAG, "Failed to copy $source", it) }
                    .getOrNull()
                    ?.let { MealPhoto(id, it).also { unsavedPhotoIds += id } }
            }
            addPhotos(copied)
            when {
                copied.size < sourceUris.take(room).size -> _uiState.update { it.copy(snackbarMessage = "Some photos couldn't be added") }
                sourceUris.size > room -> _uiState.update { it.copy(snackbarMessage = "A course holds up to $MAX_PHOTOS_PER_COURSE photos") }
            }
        }
    }

    private fun addPhotos(photos: List<MealPhoto>) {
        if (photos.isEmpty()) return
        _uiState.update { it.withoutEstimate().copy(photos = (it.photos + photos).take(MAX_PHOTOS_PER_COURSE)) }
    }

    /**
     * Changed photos invalidate the AI estimate: its foods, totals, reasoning and suggested name are
     * dropped so nothing from the old photos lingers. Values the user typed without AI are kept.
     */
    private fun AddMealState.withoutEstimate(): AddMealState {
        if (components.isEmpty() && reasoning == null) return this
        val hadFoods = components.isNotEmpty()
        return copy(
            components = emptyList(),
            reasoning = null,
            description = if (description == dishName) "" else description,
            dishName = null,
            carbohydrates = if (hadFoods) null else carbohydrates,
            proteins = if (hadFoods) null else proteins,
            fats = if (hadFoods) null else fats,
            calories = if (hadFoods) null else calories,
            impactType = if (impactAuto || hadFoods) ImpactType.MEDIUM else impactType,
            impactAuto = impactAuto || hadFoods
        )
    }

    fun onRemovePhoto(imageId: String) {
        // Files of saved photos are only deleted when the edit is saved, so cancelling keeps them.
        if (imageId in unsavedPhotoIds) {
            unsavedPhotoIds -= imageId
            viewModelScope.launch { fileStorageRepository.deleteImage(imageId) }
        }
        _uiState.update { s ->
            s.withoutEstimate().copy(photos = s.photos.filterNot { it.imageId == imageId })
        }
    }

    /** Estimates nutrition for all of this course's photos in one request. */
    fun analyzeMeal() {
        val photos = uiState.value.photos
        if (photos.isEmpty() || uiState.value.isAnalyzing) return
        viewModelScope.launch {
            val notes = uiState.value.aiNotes
            // The note stays, so a failed request can be edited and resent (or a redo can reuse it).
            _uiState.update { it.copy(isAnalyzing = true) }

            analyzeMealUseCase(photos.map { it.uri }, notes)
                .onSuccess { analysis ->
                    if (analysis.components.isEmpty()) {
                        // Nothing usable: leave the form as it was instead of filling in "No food detected".
                        _uiState.update { it.copy(isAnalyzing = false, aiRequestOpen = true, snackbarMessage = "The AI found no food in the photos") }
                        return@onSuccess
                    }
                    val totals = analysis.totals
                    _uiState.update {
                        it.copy(
                            dishName = analysis.mealName,
                            // Re-analysing replaces a name the AI suggested before, but never one the user typed.
                            description = if (it.description.isNullOrBlank() || it.description == it.dishName) analysis.mealName else it.description,
                            components = analysis.components,
                            carbohydrates = totals.carbs,
                            proteins = totals.protein,
                            fats = totals.fat,
                            calories = totals.calories,
                            // Still "suggested": it follows later edits of the values until the user picks one.
                            impactType = analysis.impactType,
                            impactAuto = true,
                            reasoning = analysis.reasoning,
                            isAnalyzing = false,
                            aiRequestOpen = false,
                            snackbarMessage = "AI estimate ready. Check the values before saving"
                        )
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "Meal analysis failed", error)
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            aiRequestOpen = true,
                            snackbarMessage = "AI analysis failed: ${error.message}"
                        )
                    }
                }
        }
    }

    /** Changes a component's weight (its nutrients scale) and refreshes the totals from the components. */
    fun onComponentWeightChanged(index: Int, weightG: Double) = updateComponents { list ->
        list.mapIndexed { i, c -> if (i == index) c.withWeight(weightG) else c }
    }

    /** Drops typed totals and uses the sum of the foods again. */
    fun onUseFoodTotals() = updateComponents { it }

    /** Edits one food's macros (null = unchanged); its energy and the meal totals follow. */
    fun onComponentMacrosChanged(index: Int, carbs: Double? = null, protein: Double? = null, fat: Double? = null) = updateComponents { list ->
        list.mapIndexed { i, c ->
            if (i == index) c.withMacros(carbs ?: c.carbsG, protein ?: c.proteinG, fat ?: c.fatG) else c
        }
    }

    fun onComponentRemoved(index: Int) = updateComponents { list -> list.filterIndexed { i, _ -> i != index } }

    /** Edits to the components re-sum the nutrition totals (overwriting manually typed ones). */
    private fun updateComponents(change: (List<MealComponent>) -> List<MealComponent>) {
        _uiState.update {
            val components = change(it.components)
            val totals = components.totals()
            val next = it.copy(
                components = components,
                carbohydrates = if (components.isEmpty()) it.carbohydrates else totals.carbs,
                proteins = if (components.isEmpty()) it.proteins else totals.protein,
                fats = if (components.isEmpty()) it.fats else totals.fat,
                calories = if (components.isEmpty()) it.calories else totals.calories
            )
            if (next.impactAuto) next.copy(impactType = inferImpactType(next.carbohydrates, next.proteins, next.fats)) else next
        }
    }

    fun saveMeal() {
        val state = uiState.value
        if (!state.canSave || state.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val original = editing
            val sittingId = if (original == null) targetSitting?.let { ensureSittingId(it) } else original.sittingId
            val result: Result<*> = if (original == null) {
                createMealUseCase(
                    photos = state.photos,
                    description = state.description?.trim(),
                    userId = userId,
                    sittingId = sittingId,
                    mealTimeUtc = state.mealTime ?: Instant.now().toEpochMilli(),
                    carbohydrates = state.carbohydrates ?: 0,
                    proteins = state.proteins,
                    fats = state.fats,
                    calories = state.totalCalories,
                    impactType = state.impactType,
                    mealType = state.mealType,
                    reasoning = state.reasoning,
                    components = state.components
                )
            } else {
                val edited = original.copy(
                    description = state.description?.trim(),
                    mealTimeUtc = state.mealTime ?: original.mealTimeUtc,
                    carbohydrates = state.carbohydrates ?: 0,
                    proteins = state.proteins,
                    fats = state.fats,
                    calories = state.totalCalories,
                    impactType = state.impactType,
                    mealType = state.mealType,
                    reasoning = state.reasoning,
                    components = state.components
                )
                updateMealUseCase(meal = edited, photos = state.photos)
            }

            if (result.isSuccess) {
                // A renamed meal carries its name on every course; an untouched default stays derived.
                val name = state.mealName.trim()
                val sitting = state.sitting
                if (sitting != null && sittingId != null && name.isNotEmpty() && name != targetSitting?.title && name != sittingTitleAtStart) {
                    mealRepository.setSittingName(sittingId, name)
                }
                unsavedPhotoIds.clear()
                _uiState.update {
                    it.copy(isLoading = false, finished = EditorResult.Saved(wasNew = original == null, addedCourse = targetSitting != null))
                }
            } else {
                Log.e(TAG, "Saving meal failed", result.exceptionOrNull())
                _uiState.update { it.copy(isLoading = false, snackbarMessage = "Couldn't save the meal") }
            }
        }
    }

    /** Meals logged before courses existed have no sitting id yet: give their courses one now. */
    private suspend fun ensureSittingId(sitting: MealSitting): String {
        sitting.sittingId?.let { return it }
        val id = UUID.randomUUID().toString()
        sitting.courses.forEach { mealRepository.setSittingId(it.id, id) }
        return id
    }

    /** Soft-deletes the course being edited. */
    fun deleteMeal() {
        val id = editing?.id ?: return
        viewModelScope.launch {
            setMealValidUseCase(id, false)
                .onSuccess { _uiState.update { it.copy(finished = EditorResult.Deleted(id)) } }
                .onFailure { _uiState.update { s -> s.copy(snackbarMessage = "Couldn't delete the meal") } }
        }
    }

    /** Shows the request card again (with the previous note) so the user can edit it and resend. */
    fun openAiRequest() {
        _uiState.update { it.copy(aiRequestOpen = true) }
    }

    fun onAiNotesChanged(notes: String) {
        _uiState.update { it.copy(aiNotes = notes) }
    }

    fun onMealNameChanged(name: String) {
        _uiState.update { it.copy(mealName = name) }
    }

    fun onDescriptionChanged(newDescription: String) {
        _uiState.update { it.copy(description = newDescription) }
    }

    fun onMealTimeChanged(newMealTime: LocalDateTime) {
        _uiState.update {
            it.copy(
                mealTime = localDateTimeToInstant(newMealTime).toEpochMilli(),
                mealType = if (it.mealTypeAuto) inferMealType(newMealTime.hour) else it.mealType
            )
        }
    }

    fun onCarbsChanged(newCarbs: String) = onMacroChanged { it.copy(carbohydrates = newCarbs.toIntOrNull()) }

    fun onProteinChanged(newProtein: String) = onMacroChanged { it.copy(proteins = newProtein.toIntOrNull()) }

    fun onFatChanged(newFat: String) = onMacroChanged { it.copy(fats = newFat.toIntOrNull()) }

    /** Applies a macro edit and, until the user picks an absorption speed themselves, re-suggests one. */
    private fun onMacroChanged(change: (AddMealState) -> AddMealState) {
        _uiState.update {
            val next = change(it)
            if (next.impactAuto) next.copy(impactType = inferImpactType(next.carbohydrates, next.proteins, next.fats)) else next
        }
    }

    fun onImpactTypeChanged(impactType: ImpactType) {
        _uiState.update { it.copy(impactType = impactType, impactAuto = false) }
    }

    fun onMealTypeChanged(mealType: MealType) {
        _uiState.update { it.copy(mealType = mealType, mealTypeAuto = false) }
    }

    fun consumeFinished() {
        _uiState.update { it.copy(finished = null) }
    }
}
