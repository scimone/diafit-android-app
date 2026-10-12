package uk.scimone.diafit.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.LlmLogTotals
import uk.scimone.diafit.core.domain.model.LlmRequestLogEntity

@Dao
interface LlmLogDao {
    @Insert
    suspend fun insert(entry: LlmRequestLogEntity)

    @Query("SELECT * FROM LlmRequestLogEntity ORDER BY timestampUtc DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LlmRequestLogEntity>>

    @Query("SELECT COUNT(*) AS requests, COALESCE(SUM(inputTokens), 0) AS inputTokens, COALESCE(SUM(outputTokens), 0) AS outputTokens FROM LlmRequestLogEntity")
    fun observeTotals(): Flow<LlmLogTotals>

    @Query("DELETE FROM LlmRequestLogEntity")
    suspend fun clear()

    /** Keeps the newest [keep] rows. */
    @Query("DELETE FROM LlmRequestLogEntity WHERE id NOT IN (SELECT id FROM LlmRequestLogEntity ORDER BY timestampUtc DESC LIMIT :keep)")
    suspend fun prune(keep: Int)
}
