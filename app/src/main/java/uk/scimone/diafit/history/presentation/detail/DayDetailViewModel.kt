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
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.core.domain.model.MealSitting
import uk.scimone.diafit.core.domain.model.toSittings
import uk.scimone.diafit.history.domain.model.DayGlucoseStats
import uk.scimone.diafit.history.domain.model.GlucoseEpisode
import uk.scimone.diafit.history.domain.model.GlucoseSample
import uk.scimone.diafit.history.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.domain.usecase.GetDayDetailUseCase
import uk.scimone.diafit.history.domain.usecase.DayDetail
import uk.scimone.diafit.history.presentation.model.toThresholds
import uk.scimone.diafit.journal.presentation.model.GlucoseImpact
import uk.scimone.diafit.journal.presentation.model.GlucoseStatus
import uk.scimone.diafit.journal.presentation.model.MealEntityUi
import uk.scimone.diafit.journal.presentation.model.toUi
import uk.scimone.diafit.settings.domain.model.toCore
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import uk.scimone.diafit.settings.presentation.SettingsChangeBus
import java.time.LocalDate
import kotlin.math.abs

/** Everything recorded on one day, as shown on the History day page. */
class DayDetailViewModel(
    private val getDayDetail: GetDayDetailUseCase,
    private val getTargetRange: GetTargetRangeUseCase,
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
            getDayDetail(userId, date)
                .map { it.toState(target) }
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    Log.e(TAG, "Failed to load $date", e)
                    _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to load this day") }
                }
                .collect { _state.value = it }
        }
    }

    private fun DayDetail.toState(target: GlucoseTargetRange): DayDetailState {
        val thresholds = target.toThresholds()
        val daySamples = readings.filter { it.timestamp in dayStartUtc until dayEndUtc }.map { GlucoseSample(it.timestamp, it.valueMgdl) }
        val stats = DayGlucoseStats.from(daySamples, thresholds)
        val dayBoluses = boluses.filter { it.timestampUtc in dayStartUtc until dayEndUtc }
        val sittings = meals.toSittings()
        return DayDetailState(
            isLoading = false,
            dayStartUtc = dayStartUtc,
            dayEndUtc = dayEndUtc,
            thresholds = thresholds,
            readings = daySamples,
            stats = stats,
            boluses = dayBoluses,
            totalCarbs = meals.sumOf { it.carbohydrates },
            bolusUnits = dayBoluses.filter { !it.isSmb }.sumOf { it.value.toDouble() },
            smbUnits = dayBoluses.filter { it.isSmb }.sumOf { it.value.toDouble() },
            meals = sittings.map { it.toDayMeal(readings, boluses, target) },
            events = buildEvents(dayBoluses, stats?.episodes.orEmpty())
        )
    }

    private fun MealSitting.toDayMeal(readings: List<CgmEntity>, boluses: List<BolusEntity>, target: GlucoseTargetRange): DayMealUi {
        val outcomeEnd = startTime + OUTCOME_WINDOW_MS
        val window = readings.filter { it.timestamp in startTime..outcomeEnd }
        val impact = when {
            System.currentTimeMillis() < outcomeEnd -> GlucoseImpact(0.0, 0.0, 0.0, GlucoseStatus.TOO_EARLY)
            window.isEmpty() -> GlucoseImpact(0.0, 0.0, 0.0, GlucoseStatus.NOT_ENOUGH_DATA)
            else -> {
                val n = window.size.toDouble()
                val slots = window.map { (it.timestamp - startTime) / SLOT_MS }.toSet().size
                val coverage = slots / (OUTCOME_WINDOW_MS / SLOT_MS).toDouble()
                GlucoseImpact(
                    timeInRange = window.count { it.valueMgdl in target.lowerBound..target.upperBound } / n * 100,
                    timeAboveRange = window.count { it.valueMgdl > target.upperBound } / n * 100,
                    timeBelowRange = window.count { it.valueMgdl < target.lowerBound } / n * 100,
                    status = if (coverage < MIN_COVERAGE) GlucoseStatus.NOT_ENOUGH_DATA else GlucoseStatus.READY
                )
            }
        }
        val atStart = readings.filter { abs(it.timestamp - startTime) <= START_MATCH_MS }.minByOrNull { abs(it.timestamp - startTime) }
        val peak = window.filter { it.timestamp >= startTime }.maxByOrNull { it.valueMgdl }
        val insulin = boluses
            .filter { it.timestampUtc in (startTime - DOSE_WINDOW_MS)..(endTime + DOSE_WINDOW_MS) }
            .sumOf { it.value.toDouble() }
        return DayMealUi(
            meal = toUi(context, impact),
            courseTimesUtc = courses.map { it.mealTimeUtc },
            insulinUnits = insulin,
            startMgdl = atStart?.valueMgdl,
            peakMgdl = peak?.valueMgdl,
            peakTimeUtc = peak?.timestamp
        )
    }

    /** Boluses one by one, SMBs bundled into runs, and lows/highs, oldest first. */
    private fun buildEvents(boluses: List<BolusEntity>, episodes: List<GlucoseEpisode>): List<DayEventUi> {
        val events = mutableListOf<DayEventUi>()
        var smbRun = mutableListOf<BolusEntity>()
        fun flushSmbs() {
            if (smbRun.isEmpty()) return
            events += DayEventUi.Smbs(smbRun.first().timestampUtc, smbRun.last().timestampUtc, smbRun.size, smbRun.sumOf { it.value.toDouble() })
            smbRun = mutableListOf()
        }
        boluses.sortedBy { it.timestampUtc }.forEach { b ->
            if (b.isSmb) {
                if (smbRun.isNotEmpty() && b.timestampUtc - smbRun.last().timestampUtc > SMB_RUN_GAP_MS) flushSmbs()
                smbRun += b
            } else {
                flushSmbs()
                events += DayEventUi.Bolus(b.timestampUtc, b.value.toDouble())
            }
        }
        flushSmbs()
        events += episodes.map { DayEventUi.Episode(it) }
        return events.sortedBy { it.timeUtc }
    }

    private companion object {
        const val TAG = "DayDetailViewModel"
        const val OUTCOME_WINDOW_MS = 4 * 60 * 60_000L
        const val SLOT_MS = 5 * 60_000L
        const val MIN_COVERAGE = 0.7
        const val START_MATCH_MS = 15 * 60_000L
        /** Insulin from 30 min before the first course to 30 min after the last counts towards a meal (as on the meal page). */
        const val DOSE_WINDOW_MS = 30 * 60_000L
        const val SMB_RUN_GAP_MS = 30 * 60_000L
    }
}

data class DayDetailState(
    val isLoading: Boolean = true,
    val dayStartUtc: Long = 0,
    val dayEndUtc: Long = 0,
    val thresholds: GlucoseThresholds = GlucoseThresholds(low = 70, high = 180),
    val readings: List<GlucoseSample> = emptyList(),
    val stats: DayGlucoseStats? = null,
    val boluses: List<BolusEntity> = emptyList(),
    val totalCarbs: Int = 0,
    val bolusUnits: Double = 0.0,
    val smbUnits: Double = 0.0,
    val meals: List<DayMealUi> = emptyList(),
    val events: List<DayEventUi> = emptyList(),
    val errorMessage: String? = null
) {
    val totalInsulin: Double get() = bolusUnits + smbUnits
    val isEmpty: Boolean get() = readings.isEmpty() && boluses.isEmpty() && meals.isEmpty()
}

/** One meal of the day with what happened around it. */
data class DayMealUi(
    val meal: MealEntityUi,
    val courseTimesUtc: List<Long>,
    /** Insulin from 30 min before the first course to 30 min after the last. */
    val insulinUnits: Double,
    val startMgdl: Int?,
    val peakMgdl: Int?,
    val peakTimeUtc: Long?
)

/** A row of the day's event log. */
sealed interface DayEventUi {
    val timeUtc: Long

    data class Bolus(override val timeUtc: Long, val units: Double) : DayEventUi
    data class Smbs(override val timeUtc: Long, val endUtc: Long, val count: Int, val units: Double) : DayEventUi
    data class Episode(val episode: GlucoseEpisode) : DayEventUi {
        override val timeUtc: Long get() = episode.startUtc
    }
}
