package uk.scimone.diafit.addmeal.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity.Companion.inferImpactType
import uk.scimone.diafit.core.domain.model.MealEntity.Companion.inferMealType
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import uk.scimone.diafit.core.domain.usecase.CreateMealUseCase
import uk.scimone.diafit.core.domain.util.localDateTimeToInstant
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

class AddMealViewModel(
    private val createMealUseCase: CreateMealUseCase,
    private val fileStorageRepository: FileStorageRepository,  // TODO: Use usecase instead of repository
    private val userId: Int,
    private var hasStartedMeal: Boolean = false,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AddMealState())
    val uiState = _uiState.asStateFlow()

    private var imageId: String = UUID.randomUUID().toString()

    fun startNewMeal() {
        hasStartedMeal = true
        imageId = UUID.randomUUID().toString()
        val now = LocalDateTime.now()

        _uiState.value = AddMealState(
            mealTime = localDateTimeToInstant(now).toEpochMilli(),
            mealType = inferMealType(now.hour),
            imageUri = null,
            description = "",
            carbohydrates = null,
            proteins = null,
            fats = null,
            calories = null,
            impactType = ImpactType.MEDIUM,
            snackbarMessage = null,
            isLoading = false
        )
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
                .onSuccess { uri -> _uiState.update { it.copy(imageUri = uri) } }
                .onFailure { _uiState.update { it.copy(snackbarMessage = "Failed to copy image") } }
        }
    }

    fun saveMeal() {
        viewModelScope.launch {
            val uri = uiState.value.imageUri ?: return@launch
            _uiState.update { it.copy(isLoading = true) }

            val newImageId = UUID.randomUUID().toString()

            val result = createMealUseCase(
                imageUri = uri,
                description = uiState.value.description,
                userId = userId,
                mealTimeUtc = uiState.value.mealTime ?: Instant.now().toEpochMilli(),
                carbohydrates = uiState.value.carbohydrates ?: 0,
                proteins = uiState.value.proteins,
                fats = uiState.value.fats,
                calories = uiState.value.calories,
                imageId = newImageId
            )

            if (result.isSuccess) {
                startNewMeal()
                _uiState.update { it.copy(snackbarMessage = "Meal added successfully", isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, snackbarMessage = "Failed to add meal") }
            }
        }
    }


    fun onImageSelected(uri: Uri) {
        _uiState.update { it.copy(imageUri = uri) }
    }

    fun onDescriptionChanged(newDescription: String) {
        _uiState.update { it.copy(description = newDescription) }
    }

    fun onMealTimeChanged(newMealTime: LocalDateTime) {
        _uiState.update {
            it.copy(
                mealTime = localDateTimeToInstant(newMealTime).toEpochMilli(),
                mealType = inferMealType(newMealTime.hour)
            )
        }
    }

    fun onCarbsChanged(newCarbs: String) {
        val carbsInt = newCarbs.toIntOrNull()
        _uiState.update { it.copy(carbohydrates = carbsInt) }
        val proteins = _uiState.value.proteins
        val fats = _uiState.value.fats
        _uiState.update { it.copy(impactType = inferImpactType(carbsInt, proteins, fats)) }
    }

    fun onProteinChanged(newProtein: String) {
        val proteinInt = newProtein.toIntOrNull()
        _uiState.update { it.copy(proteins = proteinInt) }
        val carbs = _uiState.value.carbohydrates
        val fats = _uiState.value.fats
        _uiState.update { it.copy(impactType = inferImpactType(carbs, proteinInt, fats)) }
    }

    fun onFatChanged(newFat: String) {
        val fatInt = newFat.toIntOrNull()
        _uiState.update { it.copy(fats = fatInt) }
        val carbs = _uiState.value.carbohydrates
        val proteins = _uiState.value.proteins
        _uiState.update { it.copy(impactType = inferImpactType(carbs, proteins, fatInt)) }
    }

    fun onCaloriesChanged(value: String) {
        val caloriesInt = value.toIntOrNull()
        _uiState.update { it.copy(calories = caloriesInt) }
    }

    fun onImpactTypeChanged(impactType: ImpactType) {
        _uiState.update { it.copy(impactType = impactType) }
    }

    fun onMealTypeChanged(mealType: MealType) {
        _uiState.update { it.copy(mealType = mealType) }
    }
}
