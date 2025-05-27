package uk.scimone.diafit.sync.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import uk.scimone.diafit.core.domain.usecase.SyncCgmDataUseCase

class CgmSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val syncCgmDataUseCase: SyncCgmDataUseCase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            syncCgmDataUseCase()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
