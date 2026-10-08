package uk.scimone.diafit.history.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.AgpProfile
import uk.scimone.diafit.core.domain.model.DayGlucoseStats
import uk.scimone.diafit.core.domain.model.GlucoseSample
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.domain.usecase.ClusterTreatmentsUseCase
import uk.scimone.diafit.history.domain.usecase.GetDailyHistoryUseCase
import uk.scimone.diafit.history.presentation.model.AgpMarkers
import uk.scimone.diafit.history.presentation.model.DayHistoryUi
import uk.scimone.diafit.history.presentation.model.toAgpMarkers
import java.time.DayOfWeek
import java.time.LocalDate
import uk.scimone.diafit.history.presentation.model.toThresholds
import uk.scimone.diafit.history.presentation.model.toUi
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.presentation.SettingsChangeBus

class HistoryViewModel(
    private val getDailyHistory: GetDailyHistoryUseCase,
    private val clusterTreatments: ClusterTreatmentsUseCase,
    private val getTargetRange: GetTargetRangeUseCase,
    private val userId: Int
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState(isLoading = true))
    val state: StateFlow<HistoryState> = _state

    private var observeJob: Job? = null
    private var filterJob: Job? = null
    private var rawDays: List<DayHistoryUi> = emptyList()
    private var weekdays: Set<DayOfWeek> = emptySet()
    private var page = 0
    private var range = HistoryRange.TWO_WEEKS

    init {
        observe()
        // The target range is a setting: redraw when it changes.
        viewModelScope.launch { SettingsChangeBus.settingsChanged.collect { observe() } }
    }

    /** Switches the time frame shown and jumps back to the latest period. */
    fun setRange(newRange: HistoryRange) {
        if (newRange == range) return
        range = newRange
        page = 0
        _state.update { it.copy(range = newRange, page = 0, isLoading = true) }
        observe()
    }

    /** Pages are windows of [HistoryRange.days] days; 0 is the latest, higher is older. */
    fun showOlder() = setPage(page + 1)

    fun showNewer() = setPage((page - 1).coerceAtLeast(0))

    private fun setPage(newPage: Int) {
        if (newPage == page) return
        page = newPage
        _state.update { it.copy(isLoading = true) }
        observe()
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            val target = getTargetRange().toCore()
            val thresholds = target.toThresholds()
            val range = range
            val page = page
            getDailyHistory(userId, range.days, page)
                .map { days ->
                    val ui = days.map { it.toUi(target, clusterTreatments) }
                    rawDays = ui
                    buildState(ui, thresholds, range, page, weekdays)
                }
                .flowOn(Dispatchers.Default)
                .catch { e ->
                    Log.e(TAG, "Failed to load history", e)
                    _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load history") }
                }
                .collect { next -> _state.value = next }
        }
    }

    /** Which weekdays are shown; an empty set (or all seven) means every day. Re-filters the loaded data, no new query. */
    fun setWeekdays(selected: Set<DayOfWeek>) {
        val normalized = if (selected.size == 7) emptySet() else selected
        if (normalized == weekdays) return
        weekdays = normalized
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            val current = _state.value
            _state.value = buildState(rawDays, current.thresholds, range, page, normalized)
        }
    }

    /** Filters [all] by weekday and derives everything that summarises the shown days (period stats, AGP, event markers). */
    private fun buildState(
        all: List<DayHistoryUi>,
        thresholds: GlucoseThresholds,
        range: HistoryRange,
        page: Int,
        weekdays: Set<DayOfWeek>
    ): HistoryState {
        val shown = if (weekdays.isEmpty()) all else all.filter { LocalDate.ofEpochDay(it.epochDay).dayOfWeek in weekdays }
        val agp = AgpProfile.builder().also { b ->
            shown.forEach { day ->
                day.glucose.forEach { b.add(((it.timeUtc - day.dayStartUtc) / 60_000L).toInt().coerceIn(0, 1439), it.mgdl) }
            }
        }.build()
        val samples = shown.asReversed().flatMap { day -> day.glucose.map { GlucoseSample(it.timeUtc, it.mgdl) } }
        return HistoryState(
            days = shown,
            thresholds = thresholds,
            range = range,
            page = page,
            weekdays = weekdays,
            periodFirstDay = all.lastOrNull()?.epochDay,
            periodLastDay = all.firstOrNull()?.epochDay,
            agp = agp,
            agpMarkers = shown.toAgpMarkers(),
            periodStats = DayGlucoseStats.from(samples, thresholds)
        )
    }

    private companion object {
        const val TAG = "HistoryViewModel"
    }
}

data class HistoryState(
    val days: List<DayHistoryUi> = emptyList(),
    val thresholds: GlucoseThresholds = GlucoseThresholds(low = 70, high = 180),
    val range: HistoryRange = HistoryRange.TWO_WEEKS,
    val page: Int = 0,
    /** Glucose statistics over the whole period (time-weighted across all days); null without readings. */
    val periodStats: DayGlucoseStats? = null,
    /** Ambulatory glucose profile over the whole period; null without readings. */
    val agp: AgpProfile? = null,
    val agpMarkers: AgpMarkers = AgpMarkers.EMPTY,
    /** Selected weekdays; empty = all. */
    val weekdays: Set<DayOfWeek> = emptySet(),
    /** First and last day of the period before the weekday filter (for the header label). */
    val periodFirstDay: Long? = null,
    val periodLastDay: Long? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)


/** The selectable time frames of the History screen. */
enum class HistoryRange(val days: Int, val label: String) {
    WEEK(7, "1 week"),
    TWO_WEEKS(14, "2 weeks"),
    MONTH(30, "1 month"),
    THREE_MONTHS(90, "3 months")
}
