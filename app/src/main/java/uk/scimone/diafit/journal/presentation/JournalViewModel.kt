package uk.scimone.diafit.journal.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.scimone.diafit.core.domain.model.DayGlucoseStats
import uk.scimone.diafit.core.domain.model.GlucoseEpisode
import uk.scimone.diafit.core.domain.model.GlucoseSample
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.toSittings
import uk.scimone.diafit.core.domain.model.MEAL_DOSE_LEAD_MS
import uk.scimone.diafit.core.domain.model.standalone
import uk.scimone.diafit.core.domain.repository.ActivityRepository
import uk.scimone.diafit.core.domain.repository.BolusRepository
import uk.scimone.diafit.journal.presentation.model.toUi as activityToUi
import uk.scimone.diafit.core.domain.repository.CgmRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.repository.PumpEventRepository
import uk.scimone.diafit.core.domain.model.pairTemporaryTargets
import uk.scimone.diafit.core.domain.model.toTemporaryTarget
import uk.scimone.diafit.journal.presentation.model.PumpEventUi
import uk.scimone.diafit.journal.presentation.model.toUi as pumpEventToUi
import uk.scimone.diafit.journal.presentation.model.toMergedChangeUi
import uk.scimone.diafit.core.domain.usecase.GetMealOutcomeUseCase
import uk.scimone.diafit.core.domain.usecase.MergeCarbEntriesUseCase
import uk.scimone.diafit.core.domain.model.MealMatcher
import uk.scimone.diafit.journal.presentation.model.PossibleDuplicateUi
import uk.scimone.diafit.journal.presentation.model.BolusEntryUi
import uk.scimone.diafit.journal.presentation.model.toBolusEntries
import uk.scimone.diafit.journal.presentation.model.GlucoseEpisodeUi
import uk.scimone.diafit.journal.presentation.model.GlucoseImpact
import uk.scimone.diafit.journal.presentation.model.JournalEntryUi
import uk.scimone.diafit.journal.presentation.model.MealEntityUi
import uk.scimone.diafit.journal.presentation.model.toUi
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.presentation.SettingsChangeBus
import java.time.LocalDate
import java.time.ZoneId

/**
 * Journal entries: every meal (one per sitting, with its insulin and glucose outcome) and the lows
 * and highs of the last [EPISODE_DAYS] days. Re-evaluated when meals change and every few minutes,
 * so outcomes fill in and new lows/highs appear without a manual refresh.
 */
class JournalViewModel(
    private val mealRepository: MealRepository,  // TODO: Use usecase instead of repository
    private val getMealOutcome: GetMealOutcomeUseCase,
    private val cgmRepository: CgmRepository,
    private val bolusRepository: BolusRepository,
    private val pumpEventRepository: PumpEventRepository,
    private val activityRepository: ActivityRepository,
    private val mergeCarbEntries: MergeCarbEntriesUseCase,
    private val updateMeal: uk.scimone.diafit.core.domain.usecase.UpdateMealUseCase,
    private val getTargetRangeUseCase: GetTargetRangeUseCase,
    private val context: Context,
    private val userId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(JournalUiState(isLoading = true))
    val uiState: StateFlow<JournalUiState> = _uiState

    // Declared before init: observeEntries() runs from it and reads this.
    private var range = JournalRange()

    init {
        observeEntries()
        viewModelScope.launch {
            SettingsChangeBus.settingsChanged.collect {
                // When settings change, reload data
                observeEntries()
            }
        }
    }

    private var observeJob: Job? = null

    /** Suggested (not certain enough to auto-merge) pairings of the latest meal list, by imported entry id. */
    private var suggestions: Map<Int, MealMatcher.Match> = emptyMap()

    fun mergeSuggestion(importedId: Int) {
        val match = suggestions[importedId] ?: return
        viewModelScope.launch { mergeCarbEntries.merge(match).onFailure { Log.e(TAG, "Merge failed", it) } }
    }

    fun keepSeparate(importedId: Int) {
        viewModelScope.launch { mergeCarbEntries.keepSeparate(importedId).onFailure { Log.e(TAG, "Keep separate failed", it) } }
    }

    /** Shows the entries of [newRange] only. */
    fun setRange(newRange: JournalRange) {
        if (newRange == range) return
        range = newRange
        _uiState.update { it.copy(range = newRange, isLoading = true) }
        observeEntries()
    }

    private fun observeEntries() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            val range = range
            val target = runCatching { getTargetRangeUseCase().toCore() }.getOrDefault(_uiState.value.target)
            val ticks = flow {
                while (true) {
                    emit(Unit)
                    delay(REFRESH_MS)
                }
            }
            combine(
                mealRepository.observeMealsByUserId(userId), ticks, pumpEventRepository.observeCount(userId),
                activityRepository.observeSessionsSince(range.boundsUtc().first, userId)
            ) { meals, _, _, _ -> meals }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load meals"
                        )
                    }
                }
                .collect { meals ->
                    val (from, to) = range.boundsUtc()  // re-resolved each time so "last 7 days" rolls over at midnight
                    // Imported entries that might duplicate a logged meal: offered on the meal's card.
                    val matches = MealMatcher.findMatches(meals).filter { !it.confident }
                    suggestions = matches.associateBy { it.imported.id }
                    val entries = withContext(Dispatchers.IO) {
                        mealEntries(meals.filter { it.mealTimeUtc in from..to }, target) + episodeEntries(target, from, to) + bolusEntries(meals, from, to) + pumpEventEntries(from, to) + activityEntries(from, to)
                    }
                    _uiState.update {
                        it.copy(
                            entries = entries,
                            target = target,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    /** One entry per meal: the courses of a long dinner are shown (and opened) together. */
    private suspend fun mealEntries(meals: List<MealEntity>, target: GlucoseTargetRange): List<MealEntityUi> = coroutineScope {
        meals.toSittings().map { sitting ->
            async {
                try {
                    sitting.toUi(context, getMealOutcome(userId, sitting, target))
                } catch (e: Exception) {
                    Log.e(TAG, "Error calculating meal outcome", e)
                    sitting.toUi(context, GlucoseImpact(0.0, 0.0, 0.0))
                }
            }
        }.awaitAll().map { entry ->
            val match = suggestions.values.firstOrNull { it.logged.id in entry.courseIds }
            if (match == null) entry
            else entry.copy(possibleDuplicate = PossibleDuplicateUi(match.imported.id, match.imported.mealTimeUtc, match.imported.carbohydrates))
        }
    }

    private suspend fun episodeEntries(target: GlucoseTargetRange, from: Long, to: Long): List<GlucoseEpisodeUi> {
        val now = System.currentTimeMillis()
        val episodes: List<GlucoseEpisode> = try {
            // Reading everything of a very long custom range every few minutes would be heavy: cap how far back.
            val samples = cgmRepository.getEntriesBetween(maxOf(from, now - MAX_EPISODE_DAYS * DAY_MS), minOf(to, now), userId)
                .map { GlucoseSample(it.timestamp, it.valueMgdl) }
            DayGlucoseStats.from(samples, GlucoseThresholds.from(target))?.episodes.orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error finding lows and highs", e)
            emptyList()
        }
        return episodes.map(::GlucoseEpisodeUi)
    }

    /** Boluses in the range that belong to no meal (see [standalone]). */
    private suspend fun bolusEntries(meals: List<MealEntity>, from: Long, to: Long): List<BolusEntryUi> = try {
        bolusRepository.getBolusBetween(from, to, userId).standalone(meals.toSittings()).toBolusEntries()
    } catch (e: Exception) {
        Log.e(TAG, "Error loading boluses", e)
        emptyList()
    }

    /** Device milestones (pod/site changes, ...) in the range; routine temp basals stay out of the journal. */
    private suspend fun pumpEventEntries(from: Long, to: Long): List<PumpEventUi> = try {
        val milestones = pumpEventRepository.getBetween(from, to, userId).filter { it.isMilestone }
            // AAPS sometimes sends the same event twice under different ids: keep the first of each.
            .distinctBy { Triple(it.eventType.lowercase(), it.timestampUtc / 60_000, it.durationMinutes to it.notes) }
            .distinctBy { it.toTemporaryTarget()?.let { t -> listOf(t.startUtc / 60_000, t.low, t.high, t.reason) } ?: it.id }
        // A site change and an insulin change at the same moment are one event (new pod / infusion set).
        val insulinChanges = milestones.filter { it.eventType.equals("Insulin Change", true) }.toMutableList()
        val merged = milestones.filter { it.eventType.equals("Site Change", true) }.mapNotNull { site ->
            insulinChanges.firstOrNull { kotlin.math.abs(it.timestampUtc - site.timestampUtc) < 2 * 60_000L }
                ?.also { insulinChanges.remove(it) }?.let { listOf(site, it) }
        }
        val mergedIds = merged.flatten().map { it.id }.toSet()
        val rest = milestones.filter { it.id !in mergedIds }
        val pairs = pairTemporaryTargets(rest)
        val pairedTargetIds = pairs.values.map { it.id }.toSet()
        rest.filter { it.id !in pairedTargetIds }.map { it.pumpEventToUi(pairs[it.id]?.toTemporaryTarget()) } +
            merged.map { it.toMergedChangeUi() }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading pump events", e)
        emptyList()
    }

    /** Sleep (listed on the day the user woke up) and workouts in the range, from Health Connect. */
    private suspend fun activityEntries(from: Long, to: Long): List<JournalEntryUi> = try {
        val data = activityRepository.getSessionsBetween(from, to, userId)
        val sleep = data.sleepSessions.filter { it.endUtc in from..to }.map { it.activityToUi() }
        val exercise = data.exercise.filter { it.startUtc in from..to }.map {
            it.activityToUi(activityRepository.getHeartRateBetween(it.startUtc, it.endUtc, userId))
        }
        sleep + exercise
    } catch (e: Exception) {
        Log.e(TAG, "Error loading activity", e)
        emptyList()
    }

    /** Attaches a photo to a meal that has none (it becomes the cover). */
    fun addPhoto(mealId: Int, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val meal = mealRepository.getMealById(mealId) ?: return@launch
                val photo = uk.scimone.diafit.core.domain.model.MealPhoto(java.util.UUID.randomUUID().toString(), uri)
                updateMeal(meal, listOf(photo))
            } catch (e: Exception) {
                Log.e(TAG, "Error adding photo", e)
            }
            observeEntries()
        }
    }

    /** Removes a device event from the journal (soft delete, so AAPS re-sending it doesn't bring it back). */
    fun deletePumpEvent(ids: List<Int>) {
        viewModelScope.launch {
            try {
                ids.forEach { pumpEventRepository.setDeleted(it, true) }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting pump event", e)
            }
            observeEntries()
        }
    }

    fun refreshMeals() {
        _uiState.update { it.copy(isLoading = true) }
        observeEntries()
    }

    private companion object {
        const val TAG = "JournalViewModel"
        /** Lows and highs are only searched this many days back, however wide the range. */
        const val MAX_EPISODE_DAYS = 180
        const val DAY_MS = 24 * 60 * 60_000L
        const val REFRESH_MS = 5 * 60_000L
    }
}

data class JournalUiState(
    /** Every journal entry, of any kind, in no particular order. */
    val entries: List<JournalEntryUi> = emptyList(),
    val target: GlucoseTargetRange = GlucoseTargetRange(70, 180),
    val range: JournalRange = JournalRange(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/** How far back the journal looks when no range was picked. */
const val DEFAULT_RANGE_DAYS = 30

/** The journal's time filter: the last [DEFAULT_RANGE_DAYS] days, or a picked first–last day (both inclusive). */
data class JournalRange(val customFrom: LocalDate? = null, val customTo: LocalDate? = null) {
    val isCustom: Boolean get() = customFrom != null && customTo != null

    fun days(zone: ZoneId = ZoneId.systemDefault()): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now(zone)
        return if (customFrom != null && customTo != null) customFrom to customTo
        else today.minusDays(DEFAULT_RANGE_DAYS - 1L) to today
    }

    /** Start of the first day to end of the last day, in epoch ms. */
    fun boundsUtc(zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val (from, to) = days(zone)
        return from.atStartOfDay(zone).toInstant().toEpochMilli() to to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    }
}
