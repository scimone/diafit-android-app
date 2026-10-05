package uk.scimone.diafit.core.data.worker

import android.util.Log
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds
import uk.scimone.diafit.core.domain.usecase.SyncCgmDataUseCase

private const val TAG = "CgmRemotePoller"

class CgmRemotePoller(
    private val syncCgmDataUseCase: SyncCgmDataUseCase
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    syncCgmDataUseCase()
                } catch (e: Exception) {
                    Log.e(TAG, "CGM sync failed", e)
                }
                delay(60.seconds)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
