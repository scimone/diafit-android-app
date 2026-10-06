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
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.core.domain.model.MEAL_DOSE_LEAD_MS
import uk.scimone.diafit.core.domain.model.MEAL_DOSE_TAIL_MS
import uk.scimone.diafit.core.domain.model.MealSitting
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
    /** The whole meal as one entry (summed for a multi-course meal). */
    val meal: MealEntityUi? = null,
    /** Its courses, oldest first; a single element for an ordinary meal. */
    val courses: List<MealEntityUi> = emptyList(),
    /** Boluses delivered around the meal, oldest first. */
    val boluses: List<BolusEntity> = emptyList(),
    val response: GlucoseResponse? = null,
    /** Carb entries (time, grams) inside the chart window that aren't part of this meal. */
    val otherCarbs: List<Pair<Long, Int>> = emptyList(),
    /** When the last course should have been absorbed. */
    val effectEndUtc: Long = 0L,
    val target: GlucoseTargetRange = GlucoseTargetRange(70, 180),
    val isLoading: Boolean = true,
    /** The meal no longer exists or was deleted. */
    val isGone: Boolean = false
) {
    val isMultiCourse: Boolean get() = courses.size > 1
    val totalInsulin: Double get() = boluses.sumOf { it.value.toDouble() }
}

/** The meal that course [mealId] belongs to: every course, the insulin around it and the glucose response. */
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
            // Observing the list (not a one-shot read) keeps the page current after an edit or a new course.
            mealRepository.observeMealsByUserId(userId)
                .catch { Log.e(TAG, "Failed to observe meal $mealId", it) }
                .collect { meals ->
                    val anchor = meals.firstOrNull { it.id == mealId }
                    if (anchor == null) {
                        _state.update { it.copy(isLoading = false, isGone = true) }
                        return@collect
                    }
                    val courses = anchor.sittingId
                        ?.let { id -> meals.filter { it.sittingId == id } }
                        ?.sortedBy { it.mealTimeUtc }
                        ?: listOf(anchor)
                    val sitting = MealSitting(courses)

                    val target = runCatching { getTargetRange().toCore() }.getOrDefault(_state.value.target)
                    val durationMinutes = ((sitting.effectEndTime - sitting.startTime) / 60_000L).toInt()
                    val response = runCatching {
                        getGlucoseResponse(userId, sitting.startTime, durationMinutes)
                    }.onFailure { Log.e(TAG, "Glucose response failed", it) }.getOrNull()

                    val inWindow = response?.readings.orEmpty().filter { it.timestamp in sitting.startTime..sitting.effectEndTime }
                    val impact = if (inWindow.isEmpty()) GlucoseImpact(0.0, 0.0, 0.0) else {
                        val n = inWindow.size.toDouble()
                        GlucoseImpact(
                            timeInRange = inWindow.count { it.valueMgdl in target.lowerBound..target.upperBound } / n * 100,
                            timeAboveRange = inWindow.count { it.valueMgdl > target.upperBound } / n * 100,
                            timeBelowRange = inWindow.count { it.valueMgdl < target.lowerBound } / n * 100
                        )
                    }
                    val doseWindow = (sitting.startTime - MEAL_DOSE_LEAD_MS)..(sitting.endTime + MEAL_DOSE_TAIL_MS)
                    _state.value = MealDetailState(
                        meal = sitting.toUi(context, impact),
                        courses = courses.map { it.toUi(context, GlucoseImpact(0.0, 0.0, 0.0)) },
                        boluses = response?.boluses.orEmpty().filter { it.timestampUtc in doseWindow }.sortedBy { it.timestampUtc },
                        response = response,
                        otherCarbs = response?.let { r ->
                            meals.filter { m -> m.isValid && m.carbohydrates > 0 && courses.none { it.id == m.id } && m.mealTimeUtc in r.windowStartUtc..r.windowEndUtc }
                                .map { it.mealTimeUtc to it.carbohydrates }
                        }.orEmpty(),
                        effectEndUtc = sitting.effectEndTime,
                        target = target,
                        isLoading = false
                    )
                }
        }
    }

    /** Soft-deletes every course of the meal (kept in the DB, flagged invalid, so the delete can be undone). */
    fun delete(onDone: (List<Int>) -> Unit) {
        val ids = _state.value.courses.map { it.id }.ifEmpty { listOf(mealId) }
        viewModelScope.launch {
            setMealValid(ids, false)
                .onSuccess { onDone(ids) }
                .onFailure { Log.e(TAG, "Failed to delete meal $ids", it) }
        }
    }

    private companion object {
        const val TAG = "MealDetailViewModel"
    }
}
