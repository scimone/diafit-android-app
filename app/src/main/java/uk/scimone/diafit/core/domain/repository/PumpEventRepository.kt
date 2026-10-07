package uk.scimone.diafit.core.domain.repository

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.PumpEventEntity

interface PumpEventRepository {
    /** Returns true when the event was new. */
    suspend fun insert(event: PumpEventEntity): Boolean

    fun observeLatest(eventType: String, limit: Int, userId: Int): Flow<List<PumpEventEntity>>

    suspend fun setDeleted(id: Int, deleted: Boolean)

    suspend fun getBetween(start: Long, end: Long, userId: Int): List<PumpEventEntity>
}
