package uk.scimone.diafit.core.data.repository

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.data.local.BolusDao
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.repository.BolusRepository

class BolusRepositoryImpl(
    private val bolusDao: BolusDao
) : BolusRepository {

    override suspend fun insertBolus(bolusEntity: BolusEntity) {
        bolusDao.insertBolus(bolusEntity)
    }
    override fun getAllBolusSince(start: Long, userId: Int): Flow<List<BolusEntity>> {
        return bolusDao.getAllBolusSince(start, userId)
    }

    override suspend fun getBolusBetween(start: Long, end: Long, userId: Int): List<BolusEntity> =
        bolusDao.getBolusBetween(start, end, userId)
}
