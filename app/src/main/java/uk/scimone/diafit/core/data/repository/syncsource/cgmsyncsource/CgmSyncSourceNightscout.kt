package uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource

import android.util.Log
import kotlinx.coroutines.flow.firstOrNull
import uk.scimone.diafit.core.data.local.CgmDao
import uk.scimone.diafit.core.data.networking.NightscoutApi
import uk.scimone.diafit.core.data.networking.dto.toCgmEntity
import uk.scimone.diafit.core.domain.repository.syncsource.HealthSyncSource
import uk.scimone.diafit.core.domain.util.networking.Result
import uk.scimone.diafit.core.domain.util.formatTimestamp
import java.time.Instant
import java.time.temporal.ChronoUnit

class CgmSyncSourceNightscout(
    private val nightscoutApi: NightscoutApi,
    private val cgmDao: CgmDao
) : HealthSyncSource {

    override suspend fun sync() {
        Log.d("NightscoutSync", "Starting Nightscout sync...")

        val oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli()
        val latest = cgmDao.getLatestCgm().firstOrNull()
        val fromDate = latest?.timestamp?.let { formatTimestamp(it) } ?: formatTimestamp(oneHourAgo)
        // log the date from which we are fetching CGM entries
        Log.d("NightscoutSync", "fetching from: $fromDate")

        Log.d("NightscoutSync", "Fetching CGM entries from: $fromDate")

        when (val result = nightscoutApi.getCgmEntries(fromDate)) {
            is Result.Success -> {
                val entries = result.data.map { it.toCgmEntity(userId = 1) }
                cgmDao.insertAll(entries)
                Log.d("NightscoutSync", "Synced ${entries.size} entries")
            }
            is Result.Error -> {
                Log.e("NightscoutSync", "Error syncing: ${result.error}")
            }
        }
    }

}
