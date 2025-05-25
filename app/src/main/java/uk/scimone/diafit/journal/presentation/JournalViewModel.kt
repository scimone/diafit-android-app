package uk.scimone.diafit.journal.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.repository.MealRepository

class JournalViewModel(
    private val mealRepository: MealRepository,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(JournalUiState(isLoading = true))
    val uiState: StateFlow<JournalUiState> = _uiState.asStateFlow()

    init {
        observeMeals()
    }

    private fun observeMeals() {
        viewModelScope.launch {
            mealRepository.observeMealsByUserId(userId)
                .onEach { meals ->
                    _uiState.update { it.copy(meals = meals, isLoading = false) }
                }
                .catch { e ->
                    _uiState.update {
                        it.copy(errorMessage = e.message ?: "Failed to load meals", isLoading = false)
                    }
                }
                .collect()
        }
    }
}

data class JournalUiState(
    val meals: List<MealEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
