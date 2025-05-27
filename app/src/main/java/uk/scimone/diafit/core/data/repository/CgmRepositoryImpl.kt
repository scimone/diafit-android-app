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

    override suspend fun syncCgmFromNightscout() {
        val oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli()
        val latest = cgmDao.getLatestCgm().firstOrNull()
        val fromDate = latest?.timestamp?.let { formatTimestamp(it) }
            ?: formatTimestamp(oneHourAgo)
        val result: Result<List<NightscoutEntryDto>, NetworkError> = nightscoutApi.getCgmEntries(fromDate)

        when (result) {
            is Result.Success -> {
                val entries = result.data.map { it.toCgmEntity(userId=1) }
                cgmDao.insertAll(entries)
            }
            is Result.Error -> {
                Log.e("NightscoutSync", "Error syncing: ${result.error}")
            }
        }

    }


}