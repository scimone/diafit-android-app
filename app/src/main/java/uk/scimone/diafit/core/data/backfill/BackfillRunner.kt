package uk.scimone.diafit.core.data.backfill

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.scimone.diafit.core.data.healthconnect.HealthConnectSyncer
import uk.scimone.diafit.core.data.local.ActivityDao
import uk.scimone.diafit.core.data.local.BolusDao
import uk.scimone.diafit.core.data.local.CgmDao
import uk.scimone.diafit.core.data.networking.NightscoutApi
import uk.scimone.diafit.core.data.networking.dto.NightscoutTreatmentMapper
import uk.scimone.diafit.core.data.networking.dto.toCgmEntity
import uk.scimone.diafit.core.domain.model.TimeRange
import uk.scimone.diafit.core.domain.model.TimeRanges
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.repository.PumpEventRepository
import uk.scimone.diafit.core.domain.usecase.MergeCarbEntriesUseCase
import uk.scimone.diafit.core.domain.util.formatTimestamp
import uk.scimone.diafit.core.domain.util.networking.Result
import uk.scimone.diafit.settings.domain.model.Connector
import uk.scimone.diafit.settings.domain.model.DataType

sealed interface BackfillStatus {
    data object Idle : BackfillStatus
    data class Running(val type: DataType, val connector: Connector, val progress: Float) : BackfillStatus
    /** [nothingMissing]: every part of the range already had data, so nothing was fetched. */
    data class Done(val type: DataType, val connector: Connector, val added: Int, val nothingMissing: Boolean) : BackfillStatus
    data class Failed(val type: DataType, val message: String) : BackfillStatus
}

/**
 * User-driven history import: fetches [DataType] from a connector that can backfill ([Connector.canBackfill])
 * for a chosen time range, skipping the parts of the range that already have data. Runs one job at a time
 * and is safe to repeat (every insert is de-duplicated).
 */
class BackfillRunner(
    private val nightscout: NightscoutApi,
    private val healthConnect: HealthConnectSyncer,
    private val cgmDao: CgmDao,
    private val bolusDao: BolusDao,
    private val activityDao: ActivityDao,
    private val mealRepository: MealRepository,
    private val pumpEvents: PumpEventRepository,
    private val mergeCarbEntries: MergeCarbEntriesUseCase,
    private val store: BackfillCoverageStore,
    private val userId: Int = 1
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _status = MutableStateFlow<BackfillStatus>(BackfillStatus.Idle)
    val status: StateFlow<BackfillStatus> = _status.asStateFlow()

    fun dismiss() { if (job?.isActive != true) _status.value = BackfillStatus.Idle }

    fun start(type: DataType, connector: Connector, range: TimeRange) {
        if (job?.isActive == true) return
        require(type in connector.canBackfill) { "${connector.displayName} can't backfill ${type.label}" }
        _status.value = BackfillStatus.Running(type, connector, 0f)
        job = scope.launch {
            try {
                val gaps = missing(type, range)
                if (gaps.isEmpty()) {
                    _status.value = BackfillStatus.Done(type, connector, 0, nothingMissing = true)
                    return@launch
                }
                val total = gaps.sumOf { it.length }.toFloat()
                var doneMs = 0L
                var added = 0
                for (gap in gaps) {
                    val base = doneMs
                    added += fetch(type, connector, gap) { f ->
                        _status.value = BackfillStatus.Running(type, connector, (base + f * gap.length) / total)
                    }
                    store.record(type, gap.copy(end = minOf(gap.end, System.currentTimeMillis() - RECENT_GUARD_MS)))
                    doneMs += gap.length
                }
                _status.value = BackfillStatus.Done(type, connector, added, nothingMissing = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Backfill of $type from $connector failed", e)
                _status.value = BackfillStatus.Failed(type, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** The parts of [range] that don't have data for [type] yet. */
    suspend fun missing(type: DataType, range: TimeRange): List<TimeRange> {
        val clipped = range.copy(end = minOf(range.end, System.currentTimeMillis()))
        val covered = store.fetched(type) + when (type) {
            DataType.CGM -> TimeRanges.coveredBuckets(cgmDao.getTimestampsBetween(clipped.start, clipped.end, userId), HOUR, 4)
            DataType.HEART_RATE -> TimeRanges.coveredBuckets(activityDao.getHeartRateTimestampsBetween(clipped.start, clipped.end, userId), HOUR, 3)
            DataType.STEPS -> TimeRanges.coveredBuckets(activityDao.getStepTimestampsBetween(clipped.start, clipped.end, userId), HOUR, 1)
            else -> emptyList() // sparse data: only what an earlier backfill already fetched counts
        }
        return TimeRanges.subtract(clipped, covered, minGap = HOUR)
    }

    private suspend fun fetch(type: DataType, connector: Connector, gap: TimeRange, progress: (Float) -> Unit): Int =
        when (connector) {
            Connector.HEALTH_CONNECT -> healthConnect.backfill(type, gap, progress)
            Connector.NIGHTSCOUT -> fetchNightscout(type, gap, progress)
            else -> 0
        }

    private suspend fun fetchNightscout(type: DataType, gap: TimeRange, progress: (Float) -> Unit): Int {
        val step = if (type == DataType.CGM) DAY else 7 * DAY
        val chunks = generateSequence(gap.start) { it + step }.takeWhile { it < gap.end }.toList()
        var added = 0
        chunks.forEachIndexed { i, start ->
            progress(i / chunks.size.toFloat())
            val end = minOf(start + step, gap.end)
            added += if (type == DataType.CGM) importCgm(start, end) else importTreatments(type, start, end)
        }
        progress(1f)
        return added
    }

    private suspend fun importCgm(start: Long, end: Long): Int =
        when (val r = nightscout.getCgmEntries(formatTimestamp(start), formatTimestamp(end))) {
            is Result.Success -> {
                val rows = r.data.map { it.toCgmEntity(userId) }
                cgmDao.insertAll(rows)
                rows.size
            }
            is Result.Error -> error("Nightscout glucose request failed (${r.error}).")
        }

    private suspend fun importTreatments(type: DataType, start: Long, end: Long): Int {
        val docs = when (val r = nightscout.getTreatments(start, end)) {
            is Result.Success -> r.data
            is Result.Error -> error("Nightscout treatments request failed (${r.error}).")
        }
        var added = 0
        when (type) {
            DataType.BOLUS -> {
                val existing = bolusDao.getBolusBetween(start - HOUR, end + HOUR, userId).map { it.timestampUtc }.toHashSet()
                docs.mapNotNull { NightscoutTreatmentMapper.toBolus(it, userId) }
                    .filter { existing.add(it.timestampUtc) }
                    .forEach { bolusDao.insertBolus(it); added++ }
            }
            DataType.FOOD -> {
                docs.mapNotNull { NightscoutTreatmentMapper.toMeal(it, userId) }.forEach { meal ->
                    if (meal.sourceId != null && mealRepository.existsBySourceId(meal.sourceId)) return@forEach
                    if (mealRepository.existsImportedAt(meal.mealTimeUtc, meal.carbohydrates)) return@forEach
                    if (mealRepository.createMeal(meal).isSuccess) added++
                }
                if (added > 0) runCatching { mergeCarbEntries(userId) }
            }
            DataType.BASAL -> {
                // A live temp basal (AAPS status broadcast) and the same one from Nightscout start seconds apart.
                val existing = pumpEvents.getBetween(start - HOUR, end + HOUR, userId)
                    .filter { it.eventType == "Temp Basal" }.map { it.timestampUtc }
                docs.mapNotNull { NightscoutTreatmentMapper.toTempBasal(it, userId) }
                    .filter { e -> existing.none { kotlin.math.abs(it - e.timestampUtc) < SAME_EVENT_MS } }
                    .forEach { if (pumpEvents.insert(it)) added++ }
            }
            else -> Unit
        }
        return added
    }

    private companion object {
        const val TAG = "BackfillRunner"
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
        const val SAME_EVENT_MS = 90_000L
        /** Recent time stays "not fetched": the source may not have received it yet. */
        const val RECENT_GUARD_MS = HOUR
    }
}
