package uk.scimone.diafit.core.data.healthconnect

import android.util.Log
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.scimone.diafit.core.data.local.CgmDao
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.ExerciseEntity
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.SleepStageEntity
import uk.scimone.diafit.core.domain.model.StepsEntity
import uk.scimone.diafit.core.domain.repository.ActivityRepository
import uk.scimone.diafit.settings.domain.model.Connector
import uk.scimone.diafit.settings.domain.model.DataType
import uk.scimone.diafit.settings.domain.repository.SettingsRepository
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** What the importer is doing, for the Settings screen. */
sealed interface HealthConnectSyncStatus {
    data object Idle : HealthConnectSyncStatus
    /** [progress] 0..1 across the days being imported. */
    data class Syncing(val progress: Float) : HealthConnectSyncStatus
    data class Failed(val message: String) : HealthConnectSyncStatus
}

/** How many records the last import found in Health Connect, per type (before de-duplication into rows). */
data class HealthConnectImportSummary(val heartRate: Int, val steps: Int, val sleep: Int, val exercise: Int, val days: Int) {
    fun encode() = "$heartRate,$steps,$sleep,$exercise,$days"
    val nothingFound: Boolean get() = heartRate + steps + sleep + exercise == 0

    companion object {
        fun decode(text: String?): HealthConnectImportSummary? {
            val p = text?.split(',')?.mapNotNull { it.toIntOrNull() } ?: return null
            return if (p.size == 5) HealthConnectImportSummary(p[0], p[1], p[2], p[3], p[4]) else null
        }
    }
}

/**
 * Reads heart rate, steps, sleep, exercise (and blood glucose when it is the CGM source) from Health
 * Connect into Room. Every import re-reads a window of recent days and upserts by a natural key
 * (minute, 15-minute slot, record id), so it is idempotent and also picks up data a wearable
 * uploaded late. Backfill is the same code over more days, one day per read.
 */
class HealthConnectSyncer(
    private val manager: HealthConnectManager,
    private val activityRepository: ActivityRepository,
    private val cgmDao: CgmDao,
    private val settings: SettingsRepository,
    private val userId: Int = 1
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow<HealthConnectSyncStatus>(HealthConnectSyncStatus.Idle)
    val status: StateFlow<HealthConnectSyncStatus> = _status.asStateFlow()

    /** Imports the last [days] days of every activity type the user has granted. */
    suspend fun syncActivity(days: Int = RECENT_DAYS) = mutex.withLock {
        if (!manager.isAvailable) return@withLock
        runCatching {
            val granted = manager.grantedPermissions()
            val now = Instant.now()
            val wants = DataType.ACTIVITY.associateWith { settings.getSelection(it) == Connector.HEALTH_CONNECT }
            var heartRate = 0; var steps = 0; var sleep = 0; var exercise = 0
            val dayChunks = (days downTo 1).map { daysAgo ->
                val end = now.minus(Duration.ofDays(daysAgo - 1L))
                end.minus(Duration.ofDays(1)) to end
            }
            dayChunks.forEachIndexed { i, (start, end) ->
                _status.value = HealthConnectSyncStatus.Syncing(i / dayChunks.size.toFloat())
                if (HealthConnectPermissions.activity.all { it in granted }) {
                    if (wants[DataType.HEART_RATE] == true) heartRate += readHeartRate(start, end)
                    if (wants[DataType.STEPS] == true) steps += readSteps(start, end)
                    if (wants[DataType.SLEEP] == true) sleep += readSleep(start, end)
                    if (wants[DataType.EXERCISE] == true) exercise += readExercise(start, end)
                }
            }
            val summary = HealthConnectImportSummary(heartRate, steps, sleep, exercise, days)
            Log.i(TAG, "Imported ${days}d: $heartRate heart-rate samples, $steps step slots, $sleep sleep sessions, $exercise workouts")
            settings.setHealthConnectSummary(summary.encode())
            settings.setHealthConnectLastSync(System.currentTimeMillis())
            _status.value = HealthConnectSyncStatus.Idle
        }.onFailure {
            Log.e(TAG, "Activity import failed", it)
            _status.value = HealthConnectSyncStatus.Failed(it.message ?: it.javaClass.simpleName)
        }
    }

    /**
     * Imports blood glucose as CGM readings. Resumes from the newest Health Connect reading already
     * stored; the first time it backfills [BACKFILL_DAYS]. Returns the number of readings stored.
     */
    suspend fun syncGlucose(): Int = mutex.withLock {
        if (!manager.isAvailable) return@withLock 0
        val permission = HealthConnectPermissions.glucose.first()
        if (permission !in manager.grantedPermissions()) return@withLock 0
        val now = Instant.now()
        val latest = cgmDao.getLatestTimestampBySource(CGM_SOURCE, userId)
        val from = latest?.let { Instant.ofEpochMilli(it).minus(Duration.ofMinutes(30)) }
            ?: now.minus(Duration.ofDays(BACKFILL_DAYS.toLong()))

        val readings = mutableListOf<Pair<Long, Double>>() // time, mg/dL
        var pageToken: String? = null
        do {
            val response = manager.client.readRecords(
                ReadRecordsRequest(
                    recordType = BloodGlucoseRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, now.plusSeconds(60)),
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken
                )
            )
            response.records.forEach { readings += it.time.toEpochMilli() to it.level.inMilligramsPerDeciliter }
            pageToken = response.pageToken
        } while (pageToken != null)
        if (readings.isEmpty()) return@withLock 0

        readings.sortBy { it.first }
        // The reading just before the batch seeds the first rate.
        val seed = cgmDao.getEntriesBetween(readings.first().first - SEED_LOOKBACK_MS, readings.first().first - 1, userId).lastOrNull()
        var prevTime = seed?.timestamp
        var prevValue = seed?.valueMgdl?.toDouble()
        val entities = readings.distinctBy { it.first }.map { (time, mgdl) ->
            val rate = if (prevTime != null && prevValue != null && time - prevTime!! in 1..MAX_RATE_GAP_MS) {
                (mgdl - prevValue!!) / ((time - prevTime!!) / 60_000.0) * 5
            } else 0.0
            prevTime = time
            prevValue = mgdl
            CgmEntity(
                userId = userId,
                timestamp = time,
                valueMgdl = mgdl.roundToInt(),
                fiveMinuteRateMgdl = rate.toFloat(),
                direction = directionOf(rate / 5),
                device = "Health Connect",
                source = CGM_SOURCE
            )
        }
        cgmDao.insertAll(entities)
        Log.d(TAG, "Imported ${entities.size} glucose readings")
        entities.size
    }

    private suspend fun readHeartRate(start: Instant, end: Instant): Int {
        val perMinute = HashMap<Long, MutableList<Long>>()
        var pageToken: String? = null
        do {
            val response = manager.client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken
                )
            )
            response.records.forEach { record ->
                record.samples.forEach { sample ->
                    if (sample.beatsPerMinute in MIN_BPM..MAX_BPM) {
                        perMinute.getOrPut(sample.time.toEpochMilli() / 60_000L) { mutableListOf() } += sample.beatsPerMinute
                    }
                }
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        val rows = perMinute.map { (minute, bpms) ->
            HeartRateEntity(userId = userId, timestamp = minute * 60_000L, bpm = bpms.average().roundToInt())
        }
        activityRepository.saveHeartRate(rows)
        return perMinute.values.sumOf { it.size }
    }

    private suspend fun readSteps(start: Instant, end: Instant): Int {
        // Aggregating (rather than reading raw records) lets Health Connect drop the duplicates from
        // the phone and a watch counting the same walk.
        val bucketMs = StepsEntity.STEP_BUCKET_MS
        val alignedStart = Instant.ofEpochMilli(start.toEpochMilli() / bucketMs * bucketMs)
        val groups = manager.client.aggregateGroupByDuration(
            AggregateGroupByDurationRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(alignedStart, end),
                timeRangeSlicer = Duration.ofMillis(bucketMs)
            )
        )
        val rows = groups.mapNotNull { group ->
            val count = group.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
            if (count <= 0) null else StepsEntity(userId = userId, startUtc = group.startTime.toEpochMilli(), count = count.toInt())
        }
        activityRepository.saveSteps(rows)
        return rows.size
    }

    private suspend fun readSleep(start: Instant, end: Instant): Int {
        val rows = mutableListOf<SleepStageEntity>()
        val sessionIds = mutableListOf<String>()
        var pageToken: String? = null
        do {
            val response = manager.client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken
                )
            )
            response.records.forEach { session ->
                val id = session.metadata.id
                sessionIds += id
                val sessionStart = session.startTime.toEpochMilli()
                val sessionEnd = session.endTime.toEpochMilli()
                val stages = session.stages.ifEmpty { null }
                if (stages == null) {
                    rows += SleepStageEntity(
                        userId = userId, sessionId = id, startUtc = sessionStart, endUtc = sessionEnd,
                        stage = SleepSessionRecord.STAGE_TYPE_SLEEPING, sessionStartUtc = sessionStart, sessionEndUtc = sessionEnd
                    )
                } else {
                    stages.forEach {
                        rows += SleepStageEntity(
                            userId = userId, sessionId = id, startUtc = it.startTime.toEpochMilli(), endUtc = it.endTime.toEpochMilli(),
                            stage = it.stage, sessionStartUtc = sessionStart, sessionEndUtc = sessionEnd
                        )
                    }
                }
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        if (sessionIds.isNotEmpty()) activityRepository.saveSleep(sessionIds, rows)
        return sessionIds.size
    }

    private suspend fun readExercise(start: Instant, end: Instant): Int {
        val rows = mutableListOf<ExerciseEntity>()
        var pageToken: String? = null
        do {
            val response = manager.client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken
                )
            )
            response.records.forEach {
                rows += ExerciseEntity(
                    userId = userId,
                    sourceId = it.metadata.id,
                    startUtc = it.startTime.toEpochMilli(),
                    endUtc = it.endTime.toEpochMilli(),
                    exerciseType = it.exerciseType,
                    title = it.title?.takeIf { t -> t.isNotBlank() } ?: exerciseName(it.exerciseType)
                )
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        activityRepository.saveExercise(rows)
        return rows.size
    }

    private fun exerciseName(type: Int): String =
        ExerciseSessionRecord.EXERCISE_TYPE_INT_TO_STRING_MAP[type]
            ?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Exercise"

    /** Nightscout trend names from mg/dL per minute. */
    private fun directionOf(perMinute: Double) = when {
        perMinute > 3 -> "DoubleUp"
        perMinute > 2 -> "SingleUp"
        perMinute > 1 -> "FortyFiveUp"
        perMinute >= -1 -> "Flat"
        perMinute >= -2 -> "FortyFiveDown"
        perMinute >= -3 -> "SingleDown"
        else -> "DoubleDown"
    }

    companion object {
        private const val TAG = "HealthConnectSyncer"
        /** Value of [CgmEntity.source] for readings imported from Health Connect. */
        const val CGM_SOURCE = "HealthConnect"
        const val BACKFILL_DAYS = 14
        /** Days re-read by every routine import; also catches wearables that upload late. */
        const val RECENT_DAYS = 2
        private const val PAGE_SIZE = 5000
        private const val MIN_BPM = 20L
        private const val MAX_BPM = 250L
        private const val SEED_LOOKBACK_MS = 20 * 60_000L
        private const val MAX_RATE_GAP_MS = 15 * 60_000L
    }
}
