package uk.scimone.diafit.addmeal.presentation

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealEntity.Companion.inferImpactType
import uk.scimone.diafit.core.domain.model.MealEntity.Companion.inferMealType
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.usecase.AnalyzeMealUseCase
import uk.scimone.diafit.core.domain.usecase.CreateMealUseCase
import uk.scimone.diafit.core.domain.usecase.SetMealValidUseCase
import uk.scimone.diafit.core.domain.usecase.UpdateMealUseCase
import uk.scimone.diafit.core.domain.util.localDateTimeToInstant
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

private const val TAG = "AddMealViewModel"

/** Drives the meal editor, both for logging a new meal and for editing an existing one. */
class AddMealViewModel(
    private val createMealUseCase: CreateMealUseCase,
    private val updateMealUseCase: UpdateMealUseCase,
    private val setMealValidUseCase: SetMealValidUseCase,
    private val analyzeMealUseCase: AnalyzeMealUseCase,
    private val mealRepository: MealRepository,  // TODO: Use usecase instead of repository
    private val fileStorageRepository: FileStorageRepository,  // TODO: Use usecase instead of repository
    private val userId: Int,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AddMealState())
    val uiState = _uiState.asStateFlow()

    /** File id for a photo taken/picked in this session (always fresh, so replacing never clobbers the saved photo). */
    private var imageId: String = UUID.randomUUID().toString()
    private var pendingPhoto = false
    private var photoRemoved = false
    private var editing: MealEntity? = null
    private var baseline: AddMealState = AddMealState()

    fun startNewMeal() {
        editing = null
        pendingPhoto = false
        photoRemoved = false
        imageId = UUID.randomUUID().toString()
        val now = LocalDateTime.now()
        _uiState.value = AddMealState(
            mealTime = localDateTimeToInstant(now).toEpochMilli(),
            mealType = inferMealType(now.hour),
            description = ""
        )
        baseline = _uiState.value.formFields()
    }

    fun startEditing(mealId: Int) {
        viewModelScope.launch {
            val meal = mealRepository.getMealById(mealId)
            if (meal == null) {
                _uiState.update { it.copy(snackbarMessage = "This meal no longer exists", finished = EditorResult.Deleted(mealId)) }
                return@launch
            }
            editing = meal
            pendingPhoto = false
            photoRemoved = false
            imageId = UUID.randomUUID().toString()
            _uiState.value = AddMealState(
                editingMealId = meal.id,
                imageUri = meal.imageId.takeIf { it.isNotEmpty() }?.let { fileStorageRepository.getFileProviderUri(it) },
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
                reasoning = meal.reasoning
            )
            baseline = _uiState.value.formFields()
        }
    }

    fun isDirty(): Boolean = _uiState.value.formFields() != baseline

    /** Called when the editor is left without saving. */
    fun discard() {
        if (pendingPhoto) viewModelScope.launch { fileStorageRepository.deleteImage(imageId) }
        pendingPhoto = false
    }

    fun resetSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun createCameraImageUri(): Uri {
        return fileStorageRepository.createImageUri(imageId)
    }

    fun copyGalleryImageToPrivateStorage(sourceUri: Uri) {
        viewModelScope.launch {
            fileStorageRepository.copyGalleryImageToPrivateStorage(sourceUri, imageId)
                .onSuccess { uri ->
                    pendingPhoto = true
                    _uiState.update { it.copy(imageUri = uri) }
                }
                .onFailure { _uiState.update { it.copy(snackbarMessage = "Failed to copy image") } }
        }
    }

    fun onImageSelected(uri: Uri) {
        pendingPhoto = true
        _uiState.update { it.copy(imageUri = uri) }
    }

    fun onRemovePhoto() {
        if (pendingPhoto) {
            viewModelScope.launch { fileStorageRepository.deleteImage(imageId) }
            pendingPhoto = false
            imageId = UUID.randomUUID().toString()
        }
        photoRemoved = true
        _uiState.update { it.copy(imageUri = null, reasoning = null) }
    }

    fun analyzeMeal() {
        viewModelScope.launch {
            val uri = uiState.value.imageUri ?: return@launch
            _uiState.update { it.copy(isAnalyzing = true) }

            analyzeMealUseCase(uri)
                .onSuccess { analysis ->
                    _uiState.update {
                        it.copy(
                            dishName = analysis.dishName,
                            description = it.description.takeUnless { desc -> desc.isNullOrBlank() }
                                ?: analysis.dishName,
                            carbohydrates = analysis.carbohydrates ?: it.carbohydrates,
                            proteins = analysis.protein ?: it.proteins,
                            fats = analysis.fat ?: it.fats,
                            calories = analysis.calories ?: it.calories,
                            impactType = analysis.impactType,
                            impactAuto = false,
                            reasoning = analysis.reasoning,
                            isAnalyzing = false,
                            snackbarMessage = "AI analysis complete — review the estimated values"
                        )
                    }
                }
                .onFailure { error ->
                    Log.e(TAG, "Meal analysis failed", error)
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            snackbarMessage = "AI analysis failed: ${error.message}"
                        )
                    }
                }
        }
    }

    fun saveMeal() {
        val state = uiState.value
        if (!state.canSave || state.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val original = editing
            val result: Result<*> = if (original == null) {
                createMealUseCase(
                    imageUri = state.imageUri,
                    description = state.description?.trim(),
                    userId = userId,
                    mealTimeUtc = state.mealTime ?: Instant.now().toEpochMilli(),
                    carbohydrates = state.carbohydrates ?: 0,
                    proteins = state.proteins,
                    fats = state.fats,
                    calories = state.calories,
                    imageId = imageId,
                    impactType = state.impactType,
                    mealType = state.mealType,
                    reasoning = state.reasoning
                )
            } else {
                val edited = original.copy(
                    description = state.description?.trim(),
                    mealTimeUtc = state.mealTime ?: original.mealTimeUtc,
                    carbohydrates = state.carbohydrates ?: 0,
                    proteins = state.proteins,
                    fats = state.fats,
                    calories = state.calories,
                    impactType = state.impactType,
                    mealType = state.mealType,
                    reasoning = state.reasoning
                )
                updateMealUseCase(
                    meal = edited,
                    newImageUri = if (pendingPhoto) state.imageUri else null,
                    newImageId = if (pendingPhoto) imageId else null,
                    removeImage = photoRemoved && !pendingPhoto && original.imageId.isNotEmpty()
                )
            }

            if (result.isSuccess) {
                pendingPhoto = false
                _uiState.update { it.copy(isLoading = false, finished = EditorResult.Saved(wasNew = original == null)) }
            } else {
                Log.e(TAG, "Saving meal failed", result.exceptionOrNull())
                _uiState.update { it.copy(isLoading = false, snackbarMessage = "Couldn't save the meal") }
            }
        }
    }

    /** Soft-deletes the meal being edited. */
    fun deleteMeal() {
        val id = editing?.id ?: return
        viewModelScope.launch {
            setMealValidUseCase(id, false)
                .onSuccess { _uiState.update { it.copy(finished = EditorResult.Deleted(id)) } }
                .onFailure { _uiState.update { s -> s.copy(snackbarMessage = "Couldn't delete the meal") } }
        }
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

    fun onCaloriesChanged(value: String) {
        _uiState.update { it.copy(calories = value.toIntOrNull()) }
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
