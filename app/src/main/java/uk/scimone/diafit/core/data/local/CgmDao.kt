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

    @Query("SELECT * FROM CgmEntity ORDER BY timestamp DESC LIMIT 1")
    fun getLatestCgm(): Flow<CgmEntity>

    @Query("SELECT * FROM CgmEntity WHERE timestamp >= :start AND userId == :userId ORDER BY timestamp ASC")
    fun getAllCgmSince(start: Long, userId: Int): Flow<List<CgmEntity>>


    @Query("SELECT * FROM CgmEntity WHERE userId == :userId AND timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    fun getEntriesBetween(start: Long, end: Long, userId: Int): List<CgmEntity>
}