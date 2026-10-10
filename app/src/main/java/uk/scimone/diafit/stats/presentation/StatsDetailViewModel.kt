package uk.scimone.diafit.stats.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.stats.data.LoadStatsDaysUseCase
import uk.scimone.diafit.stats.domain.StatsDay
import uk.scimone.diafit.stats.domain.StatsMetric
import uk.scimone.diafit.stats.domain.StatsPeriod
import uk.scimone.diafit.stats.domain.StatsTrendPoint
import uk.scimone.diafit.stats.domain.StatsWindow
import uk.scimone.diafit.stats.domain.trendPoints
import java.time.LocalDate

data class StatsDetailState(
    val metric: StatsMetric,
    val period: StatsPeriod,
    val offset: Int,
    val window: StatsWindow,
    val days: List<StatsDay> = emptyList(),
    /** Averages of the last few periods ending with this one; empty until loaded. */
    val trend: List<StatsTrendPoint> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val canGoNewer: Boolean get() = offset < 0
}

/** One metric over a week / month / year in full: the bars, their numbers, and how the average moved over recent periods. */
class StatsDetailViewModel(
    private val loadDays: LoadStatsDaysUseCase,
    private val getTargetRange: GetTargetRangeUseCase,
    private val userId: Int,
    metric: StatsMetric,
    period: StatsPeriod,
    offset: Int
) : ViewModel() {

    private val _state = MutableStateFlow(
        StatsDetailState(metric, period, offset, StatsWindow.of(period, LocalDate.now(), offset))
    )
    val state: StateFlow<StatsDetailState> = _state

    init { load() }

    fun goTo(date: LocalDate) {
        val s = _state.value
        val offset = StatsWindow.offsetOf(s.period, LocalDate.now(), date)
        if (offset == s.offset) return
        _state.update { it.copy(offset = offset, window = StatsWindow.of(s.period, LocalDate.now(), offset), isLoading = true, trend = emptyList()) }
        load()
    }

    fun older() = page(-1)
    fun newer() = page(+1)

    private fun page(delta: Int) {
        val s = _state.value
        val offset = (s.offset + delta).coerceAtMost(0)
        if (offset == s.offset) return
        _state.update { it.copy(offset = offset, window = StatsWindow.of(s.period, LocalDate.now(), offset), isLoading = true, trend = emptyList()) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val s = _state.value
            val thresholds = GlucoseThresholds.from(getTargetRange().toCore())
            try {
                // The chart first (one period), then the longer span the trend needs.
                val days = loadDays(userId, s.window.first, s.window.lastExclusive, thresholds)
                _state.update { if (it.window == s.window) it.copy(days = days, isLoading = false, errorMessage = null) else it }

                val windows = (TREND_PERIODS[s.period]!! - 1 downTo 0).map { StatsWindow.of(s.period, LocalDate.now(), s.offset - it) }
                val trendDays = loadDays(userId, windows.first().first, windows.last().lastExclusive, thresholds)
                _state.update { if (it.window == s.window) it.copy(trend = trendPoints(if (s.metric == StatsMetric.SLEEP_SCHEDULE) StatsMetric.SLEEP else s.metric, trendDays, windows)) else it }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load stats detail", e)
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Couldn't load stats") }
            }
        }
    }

    private companion object {
        const val TAG = "StatsDetailViewModel"
        val TREND_PERIODS = mapOf(StatsPeriod.DAY to 1, StatsPeriod.WEEK to 8, StatsPeriod.MONTH to 6, StatsPeriod.YEAR to 3)
    }
}
