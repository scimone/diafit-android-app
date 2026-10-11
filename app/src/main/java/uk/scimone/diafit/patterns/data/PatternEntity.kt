package uk.scimone.diafit.patterns.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A stored pattern: the lifecycle bookkeeping as columns, the whole card as JSON in [payload]. */
@Entity(indices = [Index(value = ["state"])])
data class PatternEntity(
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
interface PatternDao {
    @Query("SELECT * FROM PatternEntity ORDER BY priority DESC")
    fun observeAll(): Flow<List<PatternEntity>>

    @Query("SELECT * FROM PatternEntity WHERE id = :id")
    fun observe(id: String): Flow<PatternEntity?>

    @Query("SELECT * FROM PatternEntity")
    suspend fun getAll(): List<PatternEntity>

    /** Patterns that count toward the badge. */
    @Query("SELECT COUNT(*) FROM PatternEntity WHERE isRead = 0 AND state IN ('NEW', 'UPDATED')")
    fun observeUnreadCount(): Flow<Int>

    @Upsert
    suspend fun upsertAll(items: List<PatternEntity>)

    @Query("DELETE FROM PatternEntity WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("UPDATE PatternEntity SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE PatternEntity SET isRead = 1 WHERE state IN ('NEW', 'UPDATED')")
    suspend fun markAllRead()

    @Query("UPDATE PatternEntity SET state = :state, isRead = 1 WHERE id = :id")
    suspend fun setState(id: String, state: String)

    @Query("UPDATE PatternEntity SET notifiedAtUtc = :at WHERE id IN (:ids)")
    suspend fun markNotified(ids: List<String>, at: Long)
}
