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
}