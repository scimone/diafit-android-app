package uk.scimone.diafit.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.GlucoseDayRow

@Dao
interface CgmDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCgm(cgm: CgmEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cgmList: List<CgmEntity>)

    @Query("SELECT * FROM CgmEntity WHERE userId == :userId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestCgm(userId: Int): Flow<CgmEntity?>

    @Query("SELECT * FROM CgmEntity WHERE timestamp >= :start AND userId == :userId ORDER BY timestamp ASC")
    fun getAllCgmSince(start: Long, userId: Int): Flow<List<CgmEntity>>


    @Query("SELECT * FROM CgmEntity WHERE userId == :userId AND timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    fun getEntriesBetween(start: Long, end: Long, userId: Int): List<CgmEntity>

    /** Newest reading written by one source (e.g. Health Connect), to resume an import from there. */
    @Query("SELECT timestamp FROM CgmEntity WHERE userId == :userId AND timestamp >= :start AND timestamp < :end ORDER BY timestamp ASC")
    suspend fun getTimestampsBetween(start: Long, end: Long, userId: Int): List<Long>

    @Query("SELECT MAX(timestamp) FROM CgmEntity WHERE userId == :userId AND source == :source")
    suspend fun getLatestTimestampBySource(source: String, userId: Int): Long?

    /** One row per local day (see [uk.scimone.diafit.core.data.local.ActivityDao.getDailyHeartRate] for [offsetMs]). */
    @Query(
        "SELECT (timestamp + :offsetMs) / 86400000 AS day, COUNT(*) AS n, COUNT(DISTINCT timestamp / 300000) AS slots, SUM(valueMgdl) AS sum, SUM(valueMgdl * valueMgdl) AS sumSq, " +
            "SUM(valueMgdl < :veryLow) AS veryLow, SUM(valueMgdl < :low) AS low, SUM(valueMgdl > :high) AS high, SUM(valueMgdl > :veryHigh) AS veryHigh " +
            "FROM CgmEntity WHERE userId == :userId AND timestamp >= :start AND timestamp < :end GROUP BY day"
    )
    suspend fun getDailyGlucose(
        start: Long, end: Long, offsetMs: Long, veryLow: Int, low: Int, high: Int, veryHigh: Int, userId: Int
    ): List<GlucoseDayRow>
}
