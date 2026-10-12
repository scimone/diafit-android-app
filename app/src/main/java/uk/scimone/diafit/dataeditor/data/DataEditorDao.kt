package uk.scimone.diafit.dataeditor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.ExerciseEntity
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.PumpEventEntity
import uk.scimone.diafit.core.domain.model.SleepStageEntity
import uk.scimone.diafit.core.domain.model.StepsEntity

/**
 * Raw reads and writes for the data editor: one day of any record type, update, delete, restore.
 * No tables of its own. Updates use ABORT on conflict, so moving a record onto an occupied unique
 * time slot fails (SQLiteConstraintException) instead of silently replacing the other record.
 */
@Dao
interface DataEditorDao {
    // ---- glucose
    @Query("SELECT * FROM CgmEntity WHERE userId == :userId AND timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    suspend fun glucose(start: Long, end: Long, userId: Int): List<CgmEntity>
    @Update suspend fun update(e: CgmEntity)
    @Query("DELETE FROM CgmEntity WHERE id IN (:ids)") suspend fun deleteGlucose(ids: List<Int>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreGlucose(e: List<CgmEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun add(e: CgmEntity)

    // ---- insulin
    @Query("SELECT * FROM BolusEntity WHERE userId == :userId AND timestampUtc >= :start AND timestampUtc < :end ORDER BY timestampUtc DESC")
    suspend fun boluses(start: Long, end: Long, userId: Int): List<BolusEntity>
    @Update suspend fun update(e: BolusEntity)
    @Query("DELETE FROM BolusEntity WHERE id IN (:ids)") suspend fun deleteBolus(ids: List<Int>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreBolus(e: List<BolusEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun add(e: BolusEntity)

    // ---- meals (courses): soft delete, like the Logs
    @Query("SELECT * FROM MealEntity WHERE userId == :userId AND isValid = 1 AND mergedIntoId IS NULL AND mealTimeUtc >= :start AND mealTimeUtc < :end ORDER BY mealTimeUtc DESC")
    suspend fun meals(start: Long, end: Long, userId: Int): List<MealEntity>
    @Update suspend fun update(e: MealEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun add(e: MealEntity)
    @Query("UPDATE MealEntity SET isValid = :valid WHERE id IN (:ids)") suspend fun setMealsValid(ids: List<Int>, valid: Boolean)

    // ---- device events: soft delete (a repeated broadcast must not re-import them)
    @Query("SELECT * FROM PumpEventEntity WHERE userId == :userId AND isDeleted == 0 AND eventType NOT LIKE 'Temp Basal%' AND timestampUtc >= :start AND timestampUtc < :end ORDER BY timestampUtc DESC")
    suspend fun deviceEvents(start: Long, end: Long, userId: Int): List<PumpEventEntity>
    @Update suspend fun update(e: PumpEventEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun add(e: PumpEventEntity)
    @Query("UPDATE PumpEventEntity SET isDeleted = :deleted WHERE id IN (:ids)") suspend fun setEventsDeleted(ids: List<Int>, deleted: Boolean)

    // ---- workouts
    @Query("SELECT * FROM ExerciseEntity WHERE userId == :userId AND startUtc >= :start AND startUtc < :end ORDER BY startUtc DESC")
    suspend fun workouts(start: Long, end: Long, userId: Int): List<ExerciseEntity>
    @Update suspend fun update(e: ExerciseEntity)
    @Query("DELETE FROM ExerciseEntity WHERE id IN (:ids)") suspend fun deleteWorkout(ids: List<Int>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreWorkout(e: List<ExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun add(e: ExerciseEntity)

    // ---- sleep: a session is its stage rows; listed on the day it ends (the wake-up day)
    @Query("SELECT * FROM SleepStageEntity WHERE userId == :userId AND sessionId IN (SELECT DISTINCT sessionId FROM SleepStageEntity WHERE userId == :userId AND sessionEndUtc >= :start AND sessionEndUtc < :end) ORDER BY startUtc ASC")
    suspend fun sleepRows(start: Long, end: Long, userId: Int): List<SleepStageEntity>
    @Query("DELETE FROM SleepStageEntity WHERE sessionId IN (:sessionIds)") suspend fun deleteSleep(sessionIds: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSleep(rows: List<SleepStageEntity>)
    @Transaction
    suspend fun replaceSleep(sessionId: String, rows: List<SleepStageEntity>) {
        deleteSleep(listOf(sessionId))
        insertSleep(rows)
    }

    // ---- heart rate, steps
    @Query("SELECT * FROM HeartRateEntity WHERE userId == :userId AND timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    suspend fun heartRate(start: Long, end: Long, userId: Int): List<HeartRateEntity>
    @Update suspend fun update(e: HeartRateEntity)
    @Query("DELETE FROM HeartRateEntity WHERE id IN (:ids)") suspend fun deleteHeartRate(ids: List<Int>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreHeartRate(e: List<HeartRateEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun add(e: HeartRateEntity)

    @Query("SELECT * FROM StepsEntity WHERE userId == :userId AND startUtc >= :start AND startUtc < :end ORDER BY startUtc DESC")
    suspend fun steps(start: Long, end: Long, userId: Int): List<StepsEntity>
    @Update suspend fun update(e: StepsEntity)
    @Query("DELETE FROM StepsEntity WHERE id IN (:ids)") suspend fun deleteSteps(ids: List<Int>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreSteps(e: List<StepsEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun add(e: StepsEntity)
}
