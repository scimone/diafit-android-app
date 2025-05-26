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
    fun insertCGM(cgmValue: CgmEntity)

    @Query("SELECT * FROM CGMEntity ORDER BY timestamp DESC LIMIT 1")
    fun getLatestCGM(): Flow<CgmEntity>
}