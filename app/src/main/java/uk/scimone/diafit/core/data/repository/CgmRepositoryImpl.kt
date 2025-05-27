package uk.scimone.diafit.core.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.data.local.CgmDao
import uk.scimone.diafit.core.data.networking.dto.NightscoutEntryDto
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.repository.CgmRepository
import uk.scimone.diafit.core.domain.util.NetworkError
import uk.scimone.diafit.core.domain.util.Result
import uk.scimone.diafit.core.data.networking.NightscoutApi
import kotlinx.coroutines.flow.firstOrNull
import uk.scimone.diafit.core.data.mappers.toCgmEntity
import uk.scimone.diafit.core.domain.util.formatTimestamp
import java.time.temporal.ChronoUnit
import java.time.Instant



class CgmRepositoryImpl(
    private val cgmDao: CgmDao,
    private val nightscoutApi: NightscoutApi,
) : CgmRepository {

    override fun getLatestCgm(): Flow<CgmEntity> {
        return cgmDao.getLatestCgm()
    }

    override suspend fun insertCgm(cgm: CgmEntity) {
        cgmDao.insertCgm(cgm)
    }

    override suspend fun insertAll(cgmList: List<CgmEntity>) {
        cgmDao.insertAll(cgmList)
    }


}