package uk.scimone.diafit.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import uk.scimone.diafit.core.domain.model.PumpEventEntity

@Dao
interface PumpEventDao {
    /** Ignored when the same treatment (`sourceId`) was stored before. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: PumpEventEntity): Long

    @Query("SELECT * FROM PumpEventEntity WHERE userId == :userId AND timestampUtc BETWEEN :start AND :end ORDER BY timestampUtc ASC")
    suspend fun getBetween(start: Long, end: Long, userId: Int): List<PumpEventEntity>
}
