package uk.scimone.diafit.addmeal.presentation

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.core.domain.repository.FileStorageRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.usecase.CreateMealUseCase
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
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

    private fun inferImpactType() {
        val state = _uiState.value
        val carbs = state.carbohydrates
        val proteins = state.proteins
        val fats = state.fats

        if (carbs != null && proteins != null && fats != null) {
            val newImpact = when {
                carbs >= 60 && proteins >= 30 && fats >= 20 -> ImpactType.LONG
                carbs >= 40 -> ImpactType.MEDIUM
                else -> ImpactType.SHORT
            }
            _uiState.update { it.copy(impactType = newImpact) }
        }
    }

    private fun inferMealType(hour: Int): MealType {
        val inferredMealType = when (hour) {
            in 5 until 10 -> MealType.BREAKFAST
            in 11 until 15 -> MealType.LUNCH
            in 18 until 21 -> MealType.DINNER
            else -> MealType.SNACK
        }
        return inferredMealType
    }

    // Call this to start a new meal process, resets state and mealId
    fun startNewMeal() {
        if (hasStartedMeal) return
        hasStartedMeal = true
        imageId = UUID.randomUUID().toString()
        val now = LocalDateTime.now()
        val mealTimeIso = now.atZone(ZoneId.systemDefault()).toInstant().toString()

        val hour = now.hour
        val inferredMealType = inferMealType(hour)

        _uiState.value = AddMealState(
            mealTime = mealTimeIso,
            mealType = inferredMealType
        )
    }


    fun resetSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun onImageSelected(uri: Uri) {
        _uiState.update { it.copy(imageUri = uri) }
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

            val imageId = UUID.randomUUID().toString()

            val result = createMealUseCase(
                imageUri = uri,
                description = uiState.value.description,
                userId = userId,
                mealTimeUtc = uiState.value.mealTime?.let { parseIsoToEpoch(it) } ?: Instant.now().toEpochMilli(),
                carbohydrates = uiState.value.carbohydrates ?: 0,
                proteins = uiState.value.proteins,
                fats = uiState.value.fats,
                calories = uiState.value.calories,
                imageId = imageId
            )


            _uiState.update {
                if (result.isSuccess) {
                    val (meal, storedImageUri) = result.getOrThrow()
                    // Update UI state with new imageUri so image displays correctly
                    startNewMeal()
                    it.copy(imageUri = storedImageUri, snackbarMessage = "Meal added successfully", isLoading = false)
                } else {
                    it.copy(isLoading = false, snackbarMessage = "Failed to add meal")
                }
            }
        }
    }


    fun onDescriptionChanged(newDescription: String) {
        _uiState.update { it.copy(description = newDescription) }
    }

    fun onMealTimeChanged(newMealTime: LocalDateTime) {
        val instant = newMealTime.atZone(ZoneId.systemDefault()).toInstant()
        val isoString = instant.toString()

        val hour = newMealTime.hour
        val inferredMealType = inferMealType(hour)

        _uiState.update {
            it.copy(
                mealTime = isoString,
                mealType = inferredMealType
            )
        }
    }



    fun onCarbsChanged(newCarbs: String) {
        val carbsInt = newCarbs.toIntOrNull()
        _uiState.update { it.copy(carbohydrates = carbsInt) }
        inferImpactType()
    }

    fun onProteinChanged(newProtein: String) {
        val proteinInt = newProtein.toIntOrNull()
        _uiState.update { it.copy(proteins = proteinInt) }
        inferImpactType()
    }

    fun onFatChanged(newFat: String) {
        val fatInt = newFat.toIntOrNull()
        _uiState.update { it.copy(fats = fatInt) }
        inferImpactType()
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

    fun parseIsoToEpoch(isoString: String): Long {
        // TODO: Move to utils
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            Instant.now().toEpochMilli()
        }
    }

}
