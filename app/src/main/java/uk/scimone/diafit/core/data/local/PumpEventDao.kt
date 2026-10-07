package uk.scimone.diafit.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.PumpEventEntity

@Dao
interface PumpEventDao {
    /** Ignored when the same treatment (`sourceId`) was stored before. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: PumpEventEntity): Long

    @Query("SELECT * FROM PumpEventEntity WHERE userId == :userId AND isDeleted == 0 AND timestampUtc BETWEEN :start AND :end ORDER BY timestampUtc ASC")
    suspend fun getBetween(start: Long, end: Long, userId: Int): List<PumpEventEntity>

    /** The newest events of one type, newest first, updating live. */
    @Query("SELECT * FROM PumpEventEntity WHERE userId == :userId AND isDeleted == 0 AND eventType == :eventType ORDER BY timestampUtc DESC LIMIT :limit")
    fun observeLatest(eventType: String, limit: Int, userId: Int): Flow<List<PumpEventEntity>>

    @Query("SELECT * FROM PumpEventEntity WHERE id == :id")
    suspend fun getById(id: Int): PumpEventEntity?

    /** Events of one type before [before], newest first. */
    @Query("SELECT * FROM PumpEventEntity WHERE userId == :userId AND isDeleted == 0 AND eventType == :eventType AND timestampUtc < :before ORDER BY timestampUtc DESC LIMIT :limit")
    suspend fun getBefore(eventType: String, before: Long, limit: Int, userId: Int): List<PumpEventEntity>

    /** Emits whenever an event is added or removed, so lists can refresh live. */
    @Query("SELECT COUNT(*) FROM PumpEventEntity WHERE userId == :userId AND isDeleted == 0")
    fun observeCount(userId: Int): Flow<Int>

    @Query("UPDATE PumpEventEntity SET isDeleted = :deleted WHERE id == :id")
    suspend fun setDeleted(id: Int, deleted: Boolean)
}
