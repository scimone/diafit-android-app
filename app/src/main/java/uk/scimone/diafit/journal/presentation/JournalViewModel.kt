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
import uk.scimone.diafit.core.domain.repository.CgmRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.usecase.GetMealOutcomeUseCase
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
            combine(mealRepository.observeMealsByUserId(userId), ticks) { meals, _ -> meals }
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
                    val entries = withContext(Dispatchers.IO) {
                        mealEntries(meals.filter { it.mealTimeUtc in from..to }, target) + episodeEntries(target, from, to)
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
        }.awaitAll()
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

enum class JournalRangePreset(val days: Int, val label: String) {
    LAST_7(7, "Last 7 days"),
    LAST_14(14, "Last 14 days"),
    LAST_30(30, "Last 30 days"),
    LAST_90(90, "Last 90 days"),
    CUSTOM(0, "Custom range")
}

/** The journal's time filter: a preset ending today, or custom first/last days (both inclusive). */
data class JournalRange(
    val preset: JournalRangePreset = JournalRangePreset.LAST_30,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null
) {
    fun days(zone: ZoneId = ZoneId.systemDefault()): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now(zone)
        return if (preset == JournalRangePreset.CUSTOM && customFrom != null && customTo != null) customFrom to customTo
        else today.minusDays(preset.days - 1L) to today
    }

    /** Start of the first day to end of the last day, in epoch ms. */
    fun boundsUtc(zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val (from, to) = days(zone)
        return from.atStartOfDay(zone).toInstant().toEpochMilli() to to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    }
}
