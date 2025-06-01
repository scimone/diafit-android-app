package uk.scimone.diafit.journal.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.usecase.CalculateMealGlucoseImpactUseCase
import uk.scimone.diafit.journal.presentation.model.GlucoseImpact
import uk.scimone.diafit.journal.presentation.model.MealEntityUi
import uk.scimone.diafit.journal.presentation.model.toUi
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.presentation.SettingsChangeBus

class JournalViewModel(
    private val mealRepository: MealRepository,  // TODO: Use usecase instead of repository
    private val calculateMealGlucoseImpactUseCase: CalculateMealGlucoseImpactUseCase,
    private val getTargetRangeUseCase: GetTargetRangeUseCase,
    private val context: Context,   // Inject or provide from UI layer
    private val userId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(JournalUiState(isLoading = true))
    val uiState: StateFlow<JournalUiState> = _uiState

    init {
        observeMeals()
        viewModelScope.launch {
            SettingsChangeBus.settingsChanged.collect {
                // When settings change, reload data
                observeMeals()
            }
        }
    }

    private fun observeMeals() {
        viewModelScope.launch {
            mealRepository.observeMealsByUserId(userId)
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load meals"
                        )
                    }
                }
                .collect { meals ->

                    val mealUiList = meals.map { meal ->
                        viewModelScope.async(Dispatchers.IO) {
                            val impact = try {
                                val result = calculateMealGlucoseImpactUseCase(meal, getTargetRangeUseCase().toCore())
                                GlucoseImpact(
                                    timeInRange = result.timeInRange,
                                    timeAboveRange = result.timeAboveRange,
                                    timeBelowRange = result.timeBelowRange
                                )
                            } catch (e: Exception) {
                                Log.e("JournalViewModel", "Error calculating glucose impact", e)
                                GlucoseImpact(0.0, 0.0, 0.0)
                            }
                            meal.toUi(context, impact)
                        }
                    }.awaitAll()


                    _uiState.update {
                        it.copy(
                            meals = mealUiList,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }
}

data class JournalUiState(
    val meals: List<MealEntityUi> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
