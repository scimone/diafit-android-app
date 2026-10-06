package uk.scimone.diafit.journal.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.toSittings
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.usecase.CalculateMealGlucoseImpactUseCase
import uk.scimone.diafit.journal.presentation.model.GlucoseImpact
import uk.scimone.diafit.journal.presentation.model.GlucoseStatus
import uk.scimone.diafit.journal.presentation.model.JournalEntryUi
import uk.scimone.diafit.journal.presentation.model.toUi
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.presentation.SettingsChangeBus

class JournalViewModel(
    private val mealRepository: MealRepository,  // TODO: Use usecase instead of repository
    private val calculateMealGlucoseImpactUseCase: CalculateMealGlucoseImpactUseCase,
    private val getTargetRangeUseCase: GetTargetRangeUseCase,
    private val context: Context,
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

    private var observeJob: Job? = null

    private fun observeMeals() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
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

                    // One entry per meal: the courses of a long dinner are shown (and opened) together.
                    val mealUiList = meals.toSittings().map { sitting ->
                        viewModelScope.async(Dispatchers.IO) {
                            val impact = try {
                                val windowEnd = sitting.startTime + OUTCOME_WINDOW_MS
                                if (System.currentTimeMillis() < windowEnd) {
                                    GlucoseImpact(0.0, 0.0, 0.0, GlucoseStatus.TOO_EARLY)
                                } else {
                                    val result = calculateMealGlucoseImpactUseCase(
                                        sitting.startTime, windowEnd, getTargetRangeUseCase().toCore()
                                    )
                                    GlucoseImpact(
                                        timeInRange = result.timeInRange,
                                        timeAboveRange = result.timeAboveRange,
                                        timeBelowRange = result.timeBelowRange,
                                        status = if (result.coverage < MIN_COVERAGE) GlucoseStatus.NOT_ENOUGH_DATA else GlucoseStatus.READY
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e("JournalViewModel", "Error calculating glucose impact", e)
                                GlucoseImpact(0.0, 0.0, 0.0)
                            }
                            sitting.toUi(context, impact)
                        }
                    }.awaitAll()


                    _uiState.update {
                        it.copy(
                            entries = mealUiList,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    fun refreshMeals() {
        _uiState.update { it.copy(isLoading = true) }
        observeMeals()
    }

}

data class JournalUiState(
    /** Every journal entry, of any kind, in no particular order. */
    val entries: List<JournalEntryUi> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/** The journal's glucose outcome covers the 4 h after the meal and needs 70% CGM coverage. */
private const val OUTCOME_WINDOW_MS = 4 * 60 * 60_000L
private const val MIN_COVERAGE = 0.7
