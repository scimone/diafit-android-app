package uk.scimone.diafit.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.ExerciseEntity
import uk.scimone.diafit.core.domain.model.HeartRateBucket
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.SleepStageEntity
import uk.scimone.diafit.core.domain.model.StepsEntity

@Dao
interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartRate(rows: List<HeartRateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(rows: List<StepsEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepStages(rows: List<SleepStageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(rows: List<ExerciseEntity>)

    @Query("DELETE FROM SleepStageEntity WHERE sessionId IN (:sessionIds)")
    suspend fun deleteSleepSessions(sessionIds: List<String>)

    /** A session may be re-read with different stages (the watch refined it): replace it as a whole. */
    @Transaction
    suspend fun replaceSleepSessions(sessionIds: List<String>, rows: List<SleepStageEntity>) {
        deleteSleepSessions(sessionIds)
        insertSleepStages(rows)
    }

    @Query("SELECT * FROM HeartRateEntity WHERE userId == :userId AND timestamp >= :start ORDER BY timestamp ASC")
    fun observeHeartRateSince(start: Long, userId: Int): Flow<List<HeartRateEntity>>

    @Query("SELECT * FROM StepsEntity WHERE userId == :userId AND startUtc >= :start ORDER BY startUtc ASC")
    fun observeStepsSince(start: Long, userId: Int): Flow<List<StepsEntity>>

    /** Stages of every session that still overlaps [start]..now. */
    @Query("SELECT * FROM SleepStageEntity WHERE userId == :userId AND sessionEndUtc >= :start ORDER BY startUtc ASC")
    fun observeSleepSince(start: Long, userId: Int): Flow<List<SleepStageEntity>>

    @Query("SELECT * FROM ExerciseEntity WHERE userId == :userId AND endUtc >= :start ORDER BY startUtc ASC")
    fun observeExerciseSince(start: Long, userId: Int): Flow<List<ExerciseEntity>>

    @Query("SELECT timestamp FROM HeartRateEntity WHERE userId == :userId AND timestamp >= :start AND timestamp < :end ORDER BY timestamp ASC")
    suspend fun getHeartRateTimestampsBetween(start: Long, end: Long, userId: Int): List<Long>

    @Query("SELECT startUtc FROM StepsEntity WHERE userId == :userId AND startUtc >= :start AND startUtc < :end ORDER BY startUtc ASC")
    suspend fun getStepTimestampsBetween(start: Long, end: Long, userId: Int): List<Long>

    @Query("SELECT * FROM HeartRateEntity WHERE userId == :userId AND timestamp >= :start AND timestamp < :end ORDER BY timestamp ASC")
    suspend fun getHeartRateBetween(start: Long, end: Long, userId: Int): List<HeartRateEntity>

    @Query("SELECT * FROM StepsEntity WHERE userId == :userId AND startUtc >= :start AND startUtc < :end ORDER BY startUtc ASC")
    suspend fun getStepsBetween(start: Long, end: Long, userId: Int): List<StepsEntity>

    /** Stages of every session overlapping [start]..[end]. */
    @Query("SELECT * FROM SleepStageEntity WHERE userId == :userId AND sessionEndUtc > :start AND sessionStartUtc < :end ORDER BY startUtc ASC")
    suspend fun getSleepBetween(start: Long, end: Long, userId: Int): List<SleepStageEntity>

    @Query("SELECT * FROM ExerciseEntity WHERE userId == :userId AND endUtc > :start AND startUtc < :end ORDER BY startUtc ASC")
    suspend fun getExerciseBetween(start: Long, end: Long, userId: Int): List<ExerciseEntity>

    /** Heart rate averaged per 5-minute slot: compact enough to scan months of data for elevated activity. */
    @Query("SELECT (timestamp / 300000) * 300000 AS startUtc, AVG(bpm) AS bpm FROM HeartRateEntity WHERE userId == :userId AND timestamp >= :start GROUP BY startUtc ORDER BY startUtc ASC")
    fun observeHeartRateBucketsSince(start: Long, userId: Int): Flow<List<HeartRateBucket>>
}
