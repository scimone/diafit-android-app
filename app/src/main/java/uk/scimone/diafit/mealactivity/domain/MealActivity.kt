package uk.scimone.diafit.mealactivity.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.scimone.diafit.core.domain.model.MealEntity

/** The engine in force, with the model it came from (null model = baseline). */
data class ActiveEngine(val engine: MealActivityEngine, val modelVersion: Int?, val trainingRevision: String?)

/**
 * Entry point for everything that draws or sums meal activity (Home, Journal, History). This is the one algorithm that
 * replaced `CarbActivity`'s Beta(2,3) curve: callers get a [MealActivityCurve] and read the quantity they mean
 * ([MealActivityCurve.carbsRateGph] for the charts, [MealActivityCurve.carbsRemainingG] for carbs on board).
 *
 * The engine is process-wide and changes when the pipeline promotes or retires a personal model ([version] ticks so view
 * models can rebuild). The app has a single user today (userId 1); with several users this holder would need to be
 * keyed by user.
 */
object MealActivity {
    private val state = MutableStateFlow(ActiveEngine(MealActivityEngine(), null, null))
    val active: StateFlow<ActiveEngine> get() = state

    fun set(next: ActiveEngine) { state.value = next }

    /** Curve for [meal] under the active engine; never null (an invalid entry gets an empty curve). */
    fun curve(meal: MealEntity): MealActivityCurve = curve(meal.toMealInput())

    fun curve(input: MealInput): MealActivityCurve {
        val engine = state.value.engine
        return engine.curve(input) ?: engine.curve(input.copy(carbsG = 0.0, proteinG = null, fatG = null, fiberG = null))!!
    }
}

/** What the use cases call after saving a meal so its curve exists immediately (no waiting for a background job). */
fun interface MealActivityRefresher {
    suspend fun refresh(meal: MealEntity)
}
