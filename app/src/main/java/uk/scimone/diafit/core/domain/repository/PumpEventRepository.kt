package uk.scimone.diafit.core.domain.repository

import uk.scimone.diafit.core.domain.model.PumpEventEntity

interface PumpEventRepository {
    /** Returns true when the event was new. */
    suspend fun insert(event: PumpEventEntity): Boolean

    suspend fun getBetween(start: Long, end: Long, userId: Int): List<PumpEventEntity>
}
