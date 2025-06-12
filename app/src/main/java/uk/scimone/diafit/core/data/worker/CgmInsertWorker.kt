package uk.scimone.diafit.core.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.usecase.InsertCgmUseCase
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase
import uk.scimone.diafit.settings.domain.model.CgmSource

class CgmInsertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val insertCgmUseCase: InsertCgmUseCase by inject()
    private val getCgmSourceUseCase: GetCgmSourceUseCase by inject()

    companion object {
        private const val TAG = "CgmInsertWorker"
    }

    override suspend fun doWork(): Result {
        val cgmValue = inputData.getInt("cgm_value", -1)
        val rate = inputData.getFloat("rate", 0f)
        val timestamp = inputData.getLong("timestamp", 0L)

        if (cgmValue < 0 || timestamp == 0L) {
            Log.w(TAG, "Invalid input data")
            return Result.failure()
        }

        val selectedSource = getCgmSourceUseCase()

        if (selectedSource != CgmSource.JUGGLUCO) {
            Log.d(TAG, "Ignoring CGM insert for unselected source: $selectedSource")
            return Result.success()
        }

        val entity = CgmEntity(
            userId = 1,
            timestamp = timestamp,
            valueMgdl = cgmValue,
            fiveMinuteRateMgdl = rate * 5,
            device = "Freestyle Libre",
            source = "Juggluco"
        )

        return try {
            insertCgmUseCase(entity)
            Log.d(TAG, "Inserted CGM value: $cgmValue")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Insert failed", e)
            Result.retry()
        }
    }
}
