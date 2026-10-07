package uk.scimone.diafit.core.data.repository

import uk.scimone.diafit.core.data.local.PumpEventDao
import uk.scimone.diafit.core.domain.model.PumpEventEntity
import uk.scimone.diafit.core.domain.repository.PumpEventRepository

class PumpEventRepositoryImpl(private val dao: PumpEventDao) : PumpEventRepository {
    override suspend fun insert(event: PumpEventEntity): Boolean = dao.insert(event) != -1L

    override fun observeLatest(eventType: String, limit: Int, userId: Int) = dao.observeLatest(eventType, limit, userId)

    override suspend fun setDeleted(id: Int, deleted: Boolean) = dao.setDeleted(id, deleted)

    override suspend fun getBetween(start: Long, end: Long, userId: Int): List<PumpEventEntity> =
        dao.getBetween(start, end, userId)
}
