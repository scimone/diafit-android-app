package uk.scimone.diafit.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.BolusEntity

@Dao
interface BolusDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBolus(bolus: BolusEntity)

    // get all entries since defined timestamp, ordered by timestamp ascending
    @Query("SELECT * FROM BolusEntity WHERE timestampUtc >= :start ORDER BY timestampUtc ASC")
    fun getAllBolusSince(start: Long): Flow<List<BolusEntity>>
}