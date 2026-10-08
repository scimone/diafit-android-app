package uk.scimone.diafit.core.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uk.scimone.diafit.core.data.service.CgmServiceManager
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase

/**
 * Periodic safety net: OEM battery managers (notably MIUI/HyperOS) can kill the CGM
 * foreground service overnight without the app being told. WorkManager's periodic jobs
 * survive process death and get rescheduled by the OS, so this worker re-asserts the
 * service is running for whatever CGM source is currently selected.
 */
class CgmServiceWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val cgmServiceManager: CgmServiceManager by inject()
    private val getCgmSourceUseCase: GetCgmSourceUseCase by inject()

    companion object {
        private const val TAG = "CgmServiceWatchdog"
        const val UNIQUE_WORK_NAME = "cgm_service_watchdog"
    }

    override suspend fun doWork(): Result {
        val source = getCgmSourceUseCase()
        Log.d(TAG, "Watchdog tick for source: $source")
        if (source != null) cgmServiceManager.ensureRunning(source)
        return Result.success()
    }
}
