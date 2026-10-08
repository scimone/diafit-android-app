package uk.scimone.diafit.backendsync.data

import androidx.room.Dao
import androidx.room.Query
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.SleepStageEntity

/**
 * Read-only queries for the backend upload. Rows are paged by their local `id`: these tables are insert-only (a
 * re-import replaces a row, which gives it a new, higher id), so "everything after the last uploaded id" is exactly
 * what is new since the previous sync.
 */
@Dao
interface BackendSyncDao {
    @Query("SELECT * FROM CgmEntity WHERE userId = :userId AND id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun cgmAfter(userId: Int, afterId: Int, limit: Int): List<CgmEntity>

    @Query("SELECT * FROM BolusEntity WHERE userId = :userId AND id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun bolusesAfter(userId: Int, afterId: Int, limit: Int): List<BolusEntity>

    @Query("SELECT * FROM HeartRateEntity WHERE userId = :userId AND id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun heartRatesAfter(userId: Int, afterId: Int, limit: Int): List<HeartRateEntity>

    /** Sessions with a stage row newer than [afterId] (a re-imported session gets all-new rows). */
    @Query("SELECT DISTINCT sessionId FROM SleepStageEntity WHERE userId = :userId AND id > :afterId")
    suspend fun sleepSessionsChangedAfter(userId: Int, afterId: Int): List<String>

    @Query("SELECT * FROM SleepStageEntity WHERE sessionId IN (:sessionIds) ORDER BY startUtc ASC")
    suspend fun sleepStages(sessionIds: List<String>): List<SleepStageEntity>

    @Query("SELECT MAX(id) FROM SleepStageEntity WHERE userId = :userId")
    suspend fun maxSleepStageId(userId: Int): Int?

    /** Every meal, hidden ones included: edits and deletions are uploaded too (see [BackendSyncer]). */
    @Query("SELECT * FROM MealEntity WHERE userId = :userId ORDER BY id ASC")
    suspend fun allMeals(userId: Int): List<MealEntity>
}
