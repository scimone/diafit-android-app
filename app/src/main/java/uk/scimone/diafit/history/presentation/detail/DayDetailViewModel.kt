package uk.scimone.diafit.history.presentation.detail

import android.content.Context
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
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.ActivityDayStats
import uk.scimone.diafit.core.domain.model.DayGlucoseStats
import uk.scimone.diafit.core.domain.model.GlucoseSample
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.core.domain.model.MealOutcome
import uk.scimone.diafit.core.domain.model.toSittings
import uk.scimone.diafit.history.domain.usecase.DayDetail
import uk.scimone.diafit.history.domain.usecase.GetDayDetailUseCase
import uk.scimone.diafit.home.presentation.model.CarbsChartData
import uk.scimone.diafit.home.presentation.model.CgmChartData
import uk.scimone.diafit.home.presentation.model.InsulinActivityChartData
import uk.scimone.diafit.home.presentation.model.toChartData
import uk.scimone.diafit.home.presentation.model.toInsulinActivityChartData
import uk.scimone.diafit.home.presentation.model.toMealEntityUi
import uk.scimone.diafit.core.domain.model.standalone
import uk.scimone.diafit.journal.presentation.model.toBolusEntries
import uk.scimone.diafit.journal.presentation.model.GlucoseEpisodeUi
import uk.scimone.diafit.journal.presentation.model.JournalEntryUi
import uk.scimone.diafit.journal.presentation.model.toUi
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.core.domain.model.toSmbMarks
import uk.scimone.diafit.settings.presentation.SettingsChangeBus
import java.time.LocalDate
import uk.scimone.diafit.home.presentation.model.MealEntityUi as TimelineMeal

/** Everything recorded on one day, as shown on the History day page's three tabs. */
class DayDetailViewModel(
    private val getDayDetail: GetDayDetailUseCase,
    private val getTargetRange: GetTargetRangeUseCase,
    private val settingsRepository: uk.scimone.diafit.settings.domain.repository.SettingsRepository,
    private val context: Context,
    private val userId: Int,
    epochDay: Long
) : ViewModel() {

    private val date = LocalDate.ofEpochDay(epochDay)
    private val _state = MutableStateFlow(DayDetailState())
    val state: StateFlow<DayDetailState> = _state

    private var observeJob: Job? = null

    init {
        observe()
        viewModelScope.launch { SettingsChangeBus.settingsChanged.collect { observe() } }
    }

    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            val target = getTargetRange().toCore()
            val connected = settingsRepository.isActivityEnabled()
            val style = settingsRepository.getBasalStyle()
            getDayDetail(userId, date)
                .map { it.toState(target).copy(activityConnected = connected, basalStyle = style) }
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    Log.e(TAG, "Failed to load $date", e)
                    _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load this day") }
                }
                .collect { _state.value = it }
        }
    }

    private fun DayDetail.toState(target: GlucoseTargetRange): DayDetailState {
        val thresholds = GlucoseThresholds.from(target)
        val dayReadings = readings.filter { it.timestamp in dayStartUtc until dayEndUtc }
        val stats = DayGlucoseStats.from(dayReadings.map { GlucoseSample(it.timestamp, it.valueMgdl) }, thresholds)
        val dayBoluses = boluses.filter { it.timestampUtc in dayStartUtc until dayEndUtc }
        val mealCards = meals.toSittings().map { it.toUi(context, MealOutcome.of(it, readings, boluses, target)) }
        val bolusCards = dayBoluses.standalone(meals.toSittings()).toBolusEntries()
        val episodeCards = stats?.episodes.orEmpty().map(::GlucoseEpisodeUi)
        return DayDetailState(
            isLoading = false,
            dayStartUtc = dayStartUtc,
            dayEndUtc = dayEndUtc,
            target = target,
            thresholds = thresholds,
            stats = stats,
            totalCarbs = meals.sumOf { it.carbohydrates },
            bolusUnits = dayBoluses.filter { !it.isSmb }.sumOf { it.value.toDouble() },
            smbUnits = dayBoluses.filter { it.isSmb }.sumOf { it.value.toDouble() },
            bolusCount = dayBoluses.count { !it.isSmb },
            cgm = dayReadings.map { it.toChartData() },
            // Boluses from just before midnight still show their activity at the start of the day.
            insulin = boluses.filter { !it.isSmb && it.timestampUtc < dayEndUtc }.map { it.toInsulinActivityChartData() },
            carbs = meals.map { CarbsChartData(it.mealTimeUtc, it.carbohydrates, it.impactType.durationMinutes) },
            activity = activity,
            basal = basal,
            smbs = boluses.toSmbMarks(),
            timelineMeals = meals.map { it.toMealEntityUi(context) },
            activityStats = ActivityDayStats.from(activity, dayStartUtc, dayEndUtc),
            entries = (mealCards + episodeCards + bolusCards + activityCards(activity, dayStartUtc, dayEndUtc)).sortedBy { it.timeUtc }
        )
    }

    /** Sleep that ended on this day and workouts that started on it. */
    private fun activityCards(activity: ActivityData, start: Long, end: Long): List<JournalEntryUi> =
        activity.sleepSessions.filter { it.endUtc in start until end }.map { it.toUi() } +
            activity.exercise.filter { it.startUtc in start until end }.map { it.toUi(activity.heartRate) }

    private companion object {
        const val TAG = "DayDetailViewModel"
    }
}

data class DayDetailState(
    val isLoading: Boolean = true,
    val dayStartUtc: Long = 0,
    val dayEndUtc: Long = 0,
    val target: GlucoseTargetRange = GlucoseTargetRange(70, 180),
    val thresholds: GlucoseThresholds = GlucoseThresholds(low = 70, high = 180),
    val stats: DayGlucoseStats? = null,
    val totalCarbs: Int = 0,
    val bolusUnits: Double = 0.0,
    val smbUnits: Double = 0.0,
    val bolusCount: Int = 0,
    /** Charts tab: the same series the Home panels draw. */
    val cgm: List<CgmChartData> = emptyList(),
    val insulin: List<InsulinActivityChartData> = emptyList(),
    val carbs: List<CarbsChartData> = emptyList(),
    /** Charts tab: heart rate, steps, sleep and exercise. */
    val activity: ActivityData = ActivityData(),
    val basal: List<uk.scimone.diafit.core.domain.model.BasalSegment> = emptyList(),
    val smbs: List<uk.scimone.diafit.core.domain.model.SmbMark> = emptyList(),
    val basalStyle: uk.scimone.diafit.settings.domain.model.BasalStyle = uk.scimone.diafit.settings.domain.model.BasalStyle.RATE,
    val activityStats: ActivityDayStats? = null,
    val activityConnected: Boolean = false,
    /** Charts tab: courses for the meal photo strip under the panels. */
    val timelineMeals: List<TimelineMeal> = emptyList(),
    /** Journal tab: meal and low/high cards, oldest first. */
    val entries: List<JournalEntryUi> = emptyList(),
    val errorMessage: String? = null
) {
    val totalInsulin: Double get() = bolusUnits + smbUnits
    val isEmpty: Boolean get() = cgm.isEmpty() && totalInsulin == 0.0 && entries.isEmpty() && activity.isEmpty
}
