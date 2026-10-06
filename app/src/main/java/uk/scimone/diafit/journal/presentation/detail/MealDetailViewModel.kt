package uk.scimone.diafit.journal.presentation.detail

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.usecase.GetGlucoseResponseUseCase
import uk.scimone.diafit.core.domain.usecase.GlucoseResponse
import uk.scimone.diafit.core.domain.usecase.SetMealValidUseCase
import uk.scimone.diafit.journal.presentation.model.GlucoseImpact
import uk.scimone.diafit.journal.presentation.model.MealEntityUi
import uk.scimone.diafit.journal.presentation.model.toUi
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase

data class MealDetailState(
    val meal: MealEntityUi? = null,
    val response: GlucoseResponse? = null,
    val target: GlucoseTargetRange = GlucoseTargetRange(70, 180),
    val isLoading: Boolean = true,
    /** The meal no longer exists or was deleted. */
    val isGone: Boolean = false
)

class MealDetailViewModel(
    private val mealRepository: MealRepository,
    private val getGlucoseResponse: GetGlucoseResponseUseCase,
    private val getTargetRange: GetTargetRangeUseCase,
    private val setMealValid: SetMealValidUseCase,
    private val context: Context,
    private val userId: Int,
    private val mealId: Int
) : ViewModel() {

    private val _state = MutableStateFlow(MealDetailState())
    val state: StateFlow<MealDetailState> = _state

    init {
        viewModelScope.launch {
            // Observing the list (not a one-shot read) keeps the page current after an edit.
            mealRepository.observeMealsByUserId(userId)
                .catch { Log.e(TAG, "Failed to observe meal $mealId", it) }
                .collect { meals ->
                    val meal = meals.firstOrNull { it.id == mealId }
                    if (meal == null) {
                        _state.update { it.copy(isLoading = false, isGone = true) }
                        return@collect
                    }
                    val target = runCatching { getTargetRange().toCore() }.getOrDefault(_state.value.target)
                    val response = runCatching {
                        getGlucoseResponse(userId, meal.mealTimeUtc, meal.impactType.durationMinutes)
                    }.onFailure { Log.e(TAG, "Glucose response failed", it) }.getOrNull()

                    val inWindow = response?.readings.orEmpty().filter {
                        it.timestamp in meal.mealTimeUtc..(meal.mealTimeUtc + meal.impactType.durationMinutes * 60_000L)
                    }
                    val impact = if (inWindow.isEmpty()) GlucoseImpact(0.0, 0.0, 0.0) else {
                        val n = inWindow.size.toDouble()
                        GlucoseImpact(
                            timeInRange = inWindow.count { it.valueMgdl in target.lowerBound..target.upperBound } / n * 100,
                            timeAboveRange = inWindow.count { it.valueMgdl > target.upperBound } / n * 100,
                            timeBelowRange = inWindow.count { it.valueMgdl < target.lowerBound } / n * 100
                        )
                    }
                    _state.value = MealDetailState(
                        meal = meal.toUi(context, impact),
                        response = response,
                        target = target,
                        isLoading = false
                    )
                }
        }
    }

    /** Soft-deletes the meal (it stays in the DB, flagged invalid, so the delete can be undone). */
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            setMealValid(mealId, false)
                .onSuccess { onDone() }
                .onFailure { Log.e(TAG, "Failed to delete meal $mealId", it) }
        }
    }

    private companion object {
        const val TAG = "MealDetailViewModel"
    }
}
