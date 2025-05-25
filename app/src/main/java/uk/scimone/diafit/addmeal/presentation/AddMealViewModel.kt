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
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.repository.MealRepository
import java.io.File
import java.time.Instant
import java.util.*

class AddMealViewModel(
    private val mealRepository: MealRepository,
    private val userId: String,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AddMealState())
    val uiState = _uiState.asStateFlow()

    private var mealId: String = UUID.randomUUID().toString()

    // Call this to start a new meal process, resets state and mealId
    fun startNewMeal() {
        mealId = UUID.randomUUID().toString()
        _uiState.value = AddMealState() // reset UI state to empty
    }

    fun resetSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun onImageSelected(uri: Uri) {
        _uiState.update { it.copy(imageUri = uri) }
    }

    fun createCameraImageUri(): Uri {
        val context = getApplication<Application>()
        val picturesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DiaFit")
        if (!picturesDir.exists()) picturesDir.mkdirs()

        val file = File(picturesDir, "meal_$mealId.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun copyGalleryImageToPrivateStorage(context: Context, sourceUri: Uri) {
        viewModelScope.launch {
            runCatching {
                val picturesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DiaFit")
                if (!picturesDir.exists()) picturesDir.mkdirs()

                val destFile = File(picturesDir, "meal_$mealId.jpg")
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }

                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
            }.onSuccess { destUri ->
                _uiState.update { it.copy(imageUri = destUri) }
            }.onFailure {
                _uiState.update { it.copy(snackbarMessage = "Failed to copy image") }
            }
        }
    }

    fun saveMeal() {
        viewModelScope.launch {
            val uri = uiState.value.imageUri ?: return@launch
            _uiState.update { it.copy(isLoading = true) }

            val storedResult = mealRepository.storeImage(mealId, uri)
            if (storedResult.isFailure) {
                _uiState.update { it.copy(isLoading = false, snackbarMessage = "Failed to store image") }
                return@launch
            }

            val meal = MealEntity(
                id = mealId,
                userId = userId,
                description = uiState.value.description,
                createdAtUtc = Instant.now().toEpochMilli(),
                mealTimeUtc = uiState.value.mealTime?.let { parseIsoToEpoch(it) } ?: Instant.now().toEpochMilli(),
                calories = null,
                carbohydrates = uiState.value.carbohydrates ?: 0,
                proteins = uiState.value.proteins,
                fats = uiState.value.fats,
                isValid = true,
                imageId = mealId,
                recommendation = null,
                reasoning = null
            )


            val result = mealRepository.createMeal(meal)
            _uiState.update {
                if (result.isSuccess) {
                    // Meal saved successfully: reset state and generate new mealId for next meal
                    startNewMeal()
                    it.copy(snackbarMessage = "Meal added successfully", isLoading = false)
                } else {
                    it.copy(isLoading = false, snackbarMessage = "Failed to add meal")
                }
            }
        }
    }

    fun onDescriptionChanged(newDescription: String) {
        _uiState.update { it.copy(description = newDescription) }
    }

    fun onMealTimeChanged(newMealTime: String) {
        _uiState.update { it.copy(mealTime = newMealTime) }
    }

    fun onCarbsChanged(newCarbs: String) {
        val carbsInt = newCarbs.toIntOrNull()
        _uiState.update { it.copy(carbohydrates = carbsInt) }
    }

    fun onProteinChanged(newProtein: String) {
        val proteinInt = newProtein.toIntOrNull()
        _uiState.update { it.copy(proteins = proteinInt) }
    }

    fun onFatChanged(newFat: String) {
        val fatInt = newFat.toIntOrNull()
        _uiState.update { it.copy(fats = fatInt) }
    }

    fun parseIsoToEpoch(isoString: String): Long {
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            Instant.now().toEpochMilli()
        }
    }

}
