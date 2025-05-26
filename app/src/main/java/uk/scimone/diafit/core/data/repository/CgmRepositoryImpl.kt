package uk.scimone.diafit.core.data.repository

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.data.local.CgmDao
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.repository.CgmRepository

class CgmRepositoryImpl(
    private val cgmDao: CgmDao
) : CgmRepository {

    override fun getLatestCGM(): Flow<CgmEntity> {
        return cgmDao.getLatestCGM()
    }

    override suspend fun insertCGMValue(cgmValue: CgmEntity) {
        cgmDao.insertCGM(cgmValue)
    }
}