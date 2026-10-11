package uk.scimone.diafit.patterns.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.ExerciseEntity
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.core.domain.model.MealSitting
import uk.scimone.diafit.core.domain.model.SleepSession
import uk.scimone.diafit.core.domain.model.toSittings
import uk.scimone.diafit.core.domain.repository.ActivityRepository
import uk.scimone.diafit.core.domain.repository.BolusRepository
import uk.scimone.diafit.core.domain.repository.CgmRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.patterns.domain.PatternExample
import uk.scimone.diafit.history.presentation.model.toThresholds
import uk.scimone.diafit.patterns.data.PatternStore
import uk.scimone.diafit.patterns.domain.StoredPattern
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase

/** The pattern list and the unread badge (one instance for the whole activity, so the menu badge and the page agree). */
class PatternsViewModel(private val store: PatternStore) : ViewModel() {
    val patterns: StateFlow<List<StoredPattern>?> =
        store.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val unreadCount: StateFlow<Int> =
        store.observeUnreadCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markAllRead() { viewModelScope.launch(NonCancellable) { store.markAllRead() } }
}

/** What happened during one example's window, loaded from the database when the page opens. */
data class ExampleData(
    val readings: List<CgmEntity>,
    val meals: List<MealSitting>,
    val boluses: List<BolusEntity>,
    val workouts: List<ExerciseEntity>,
    val sleep: List<SleepSession>
)

/** One pattern's page: opening it counts as reviewing it. */
class PatternDetailViewModel(
    private val store: PatternStore,
    private val getTargetRange: GetTargetRangeUseCase,
    private val cgm: CgmRepository,
    private val bolusRepository: BolusRepository,
    private val mealRepository: MealRepository,
    private val activityRepository: ActivityRepository,
    private val id: String
) : ViewModel() {
    val pattern: StateFlow<StoredPattern?> = store.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _thresholds = MutableStateFlow<GlucoseThresholds?>(null)
    val thresholds: StateFlow<GlucoseThresholds?> = _thresholds

    /** Data per example, in the order of the card's cases; null until loaded. */
    private val _examples = MutableStateFlow<List<ExampleData>?>(null)
    val examples: StateFlow<List<ExampleData>?> = _examples

    init {
        viewModelScope.launch {
            store.markRead(id)
            try { _thresholds.value = getTargetRange().toCore().toThresholds() } catch (e: Exception) { Log.e("Patterns", "No target range", e) }
        }
        viewModelScope.launch {
            // The cases only change when a scan rewrites the card.
            pattern.map { it?.card?.cases }.filterNotNull().distinctUntilChanged().collect { cases ->
                _examples.value = try { load(cases) } catch (e: Exception) { Log.e("Patterns", "Example data", e); emptyList() }
            }
        }
    }

    private suspend fun load(cases: List<PatternExample>): List<ExampleData> = withContext(Dispatchers.IO) {
        if (cases.isEmpty()) return@withContext emptyList()
        val from = cases.minOf { it.fromUtc }
        val sittings = mealRepository.getUnmergedSince(USER_ID, from - 6 * 3_600_000L).toSittings()
        cases.map { c ->
            val activity = activityRepository.getSessionsBetween(c.fromUtc, c.toUtc, USER_ID)
            ExampleData(
                readings = cgm.getEntriesBetween(c.fromUtc, c.toUtc, USER_ID).filter { it.valueMgdl > 0 }.sortedBy { it.timestamp },
                meals = sittings.filter { it.startTime in c.fromUtc..c.toUtc },
                boluses = bolusRepository.getBolusBetween(c.fromUtc, c.toUtc, USER_ID),
                workouts = activity.exercise.filter { it.endUtc > c.fromUtc && it.startUtc < c.toUtc },
                sleep = activity.sleepSessions.filter { it.endUtc > c.fromUtc && it.startUtc < c.toUtc }
            )
        }
    }

    fun dismiss() { viewModelScope.launch(NonCancellable) { store.dismiss(id) } }
    fun restore() { viewModelScope.launch(NonCancellable) { store.restore(id) } }

    private companion object { const val USER_ID = 1 }
}
