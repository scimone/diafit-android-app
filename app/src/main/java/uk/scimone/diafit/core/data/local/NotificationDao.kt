package uk.scimone.diafit.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.AppNotificationEntity

@Dao
interface NotificationDao {
    /** Returns -1 when an alert with the same key already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: AppNotificationEntity): Long

    @Query("SELECT * FROM AppNotificationEntity WHERE isDeleted = 0 ORDER BY timestampUtc DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AppNotificationEntity>>

    @Query("SELECT COUNT(*) FROM AppNotificationEntity WHERE isRead = 0 AND isDeleted = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("UPDATE AppNotificationEntity SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead()

    @Query("UPDATE AppNotificationEntity SET isDeleted = :deleted WHERE id = :id")
    suspend fun setDeleted(id: Int, deleted: Boolean)

    @Query("DELETE FROM AppNotificationEntity WHERE timestampUtc < :before")
    suspend fun deleteOlderThan(before: Long)
}
