package uk.scimone.diafit.insights.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A stored insight: the lifecycle bookkeeping as columns, the whole card as JSON in [payload]. */
@Entity(indices = [Index(value = ["state"])])
data class InsightEntity(
    @PrimaryKey val id: String,
    val state: String,
    val isRead: Boolean,
    val priority: Double,
    val firstSeenUtc: Long,
    val lastSeenUtc: Long,
    val missedScans: Int,
    val referenceEffect: Double,
    val notifiedAtUtc: Long?,
    val payload: String
)

@Dao
interface InsightDao {
    @Query("SELECT * FROM InsightEntity ORDER BY priority DESC")
    fun observeAll(): Flow<List<InsightEntity>>

    @Query("SELECT * FROM InsightEntity WHERE id = :id")
    fun observe(id: String): Flow<InsightEntity?>

    @Query("SELECT * FROM InsightEntity")
    suspend fun getAll(): List<InsightEntity>

    /** Insights that count toward the badge. */
    @Query("SELECT COUNT(*) FROM InsightEntity WHERE isRead = 0 AND state IN ('NEW', 'UPDATED')")
    fun observeUnreadCount(): Flow<Int>

    @Upsert
    suspend fun upsertAll(items: List<InsightEntity>)

    @Query("DELETE FROM InsightEntity WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("UPDATE InsightEntity SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE InsightEntity SET isRead = 1 WHERE state IN ('NEW', 'UPDATED')")
    suspend fun markAllRead()

    @Query("UPDATE InsightEntity SET state = :state, isRead = 1 WHERE id = :id")
    suspend fun setState(id: String, state: String)

    @Query("UPDATE InsightEntity SET notifiedAtUtc = :at WHERE id IN (:ids)")
    suspend fun markNotified(ids: List<String>, at: Long)
}
