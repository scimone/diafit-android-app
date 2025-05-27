package uk.scimone.diafit.core.data.repository

import android.util.Log
import kotlinx.coroutines.flow.firstOrNull
import uk.scimone.diafit.core.data.local.CgmDao
import uk.scimone.diafit.core.data.mappers.toCgmEntity
import uk.scimone.diafit.core.data.networking.NightscoutApi
import uk.scimone.diafit.core.domain.repository.CgmSyncSource
import uk.scimone.diafit.core.domain.util.Result
import uk.scimone.diafit.core.domain.util.formatTimestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

class NightscoutCgmSyncSource(
    private val nightscoutApi: NightscoutApi,
    private val cgmDao: CgmDao
) : CgmSyncSource {

    override suspend fun sync() {
        val oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli()
        val latest = cgmDao.getLatestCgm().firstOrNull()
        val fromDate = latest?.timestamp?.let { formatTimestamp(it) } ?: formatTimestamp(oneHourAgo)

        when (val result = nightscoutApi.getCgmEntries(fromDate)) {
            is Result.Success -> {
                val entries = result.data.map { it.toCgmEntity(userId = 1) }
                cgmDao.insertAll(entries)
            }
            is Result.Error -> {
                Log.e("NightscoutSync", "Error syncing: ${result.error}")
            }
        }
    }
}
