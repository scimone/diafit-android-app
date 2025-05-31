package uk.scimone.diafit.core.data.repository

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.data.local.CgmDao
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.repository.CgmRepository


class CgmRepositoryImpl(
    private val cgmDao: CgmDao,
) : CgmRepository {

    override fun getLatestCgm(): Flow<CgmEntity> {
        return cgmDao.getLatestCgm()
    }

    override fun getAllCgmSince(startOfDay: Long, userId: Int): Flow<List<CgmEntity>> {
        return cgmDao.getAllCgmSince(startOfDay, userId)
    }

    override suspend fun getEntriesBetween(start: Long, end: Long, userId: Int): List<CgmEntity> {
        return cgmDao.getEntriesBetween(start, end, userId)
    }

    override suspend fun insertCgm(cgm: CgmEntity) {
        cgmDao.insertCgm(cgm)
    }

    override suspend fun insertAll(cgmList: List<CgmEntity>) {
        cgmDao.insertAll(cgmList)
    }


}