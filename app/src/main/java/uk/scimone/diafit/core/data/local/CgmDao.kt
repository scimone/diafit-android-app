package uk.scimone.diafit.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.CgmEntity

@Dao
interface CgmDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCgm(cgm: CgmEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cgmList: List<CgmEntity>)

    @Query("SELECT * FROM CgmEntity WHERE userId == :userId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestCgm(userId: Int): Flow<CgmEntity>

    @Query("SELECT * FROM CgmEntity WHERE timestamp >= :start AND userId == :userId ORDER BY timestamp ASC")
    fun getAllCgmSince(start: Long, userId: Int): Flow<List<CgmEntity>>


    @Query("SELECT * FROM CgmEntity WHERE userId == :userId AND timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    fun getEntriesBetween(start: Long, end: Long, userId: Int): List<CgmEntity>

    /** Newest reading written by one source (e.g. Health Connect), to resume an import from there. */
    @Query("SELECT timestamp FROM CgmEntity WHERE userId == :userId AND timestamp >= :start AND timestamp < :end ORDER BY timestamp ASC")
    suspend fun getTimestampsBetween(start: Long, end: Long, userId: Int): List<Long>

    @Query("SELECT MAX(timestamp) FROM CgmEntity WHERE userId == :userId AND source == :source")
    suspend fun getLatestTimestampBySource(source: String, userId: Int): Long?
}
