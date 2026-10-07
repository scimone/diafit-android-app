package uk.scimone.diafit.core.data.repository.syncsource.cgmsyncsource

import android.util.Log
import uk.scimone.diafit.core.data.healthconnect.HealthConnectSyncer
import uk.scimone.diafit.core.domain.repository.syncsource.HealthSyncSource

/** Blood glucose written to Health Connect by a CGM app (Libre/Dexcom companions, Juggluco, ...), polled like Nightscout. */
class CgmSyncSourceHealthConnect(private val syncer: HealthConnectSyncer) : HealthSyncSource {
    override suspend fun sync() {
        runCatching { syncer.syncGlucose() }
            .onFailure { Log.e("HealthConnectCgm", "Glucose import failed", it) }
    }
}
