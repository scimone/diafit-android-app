package uk.scimone.diafit.core.data.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import uk.scimone.diafit.core.domain.repository.syncsource.IntentCgmSyncSource
import uk.scimone.diafit.core.domain.usecase.InsertCgmUseCase
import uk.scimone.diafit.settings.domain.model.CgmSource
import uk.scimone.diafit.settings.domain.usecase.GetCgmSourceUseCase

class CgmInsertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val insertCgmUseCase: InsertCgmUseCase by inject()
    private val getCgmSourceUseCase: GetCgmSourceUseCase by inject()

    private val juggluco: IntentCgmSyncSource by inject(named("JUGGLUCO"))
    private val xdrip: IntentCgmSyncSource by inject(named("XDRIP"))

    companion object {
        private const val TAG = "CgmInsertWorker"
    }

    override suspend fun doWork(): Result {
        val selectedSource = getCgmSourceUseCase()

        val intent = reconstructIntent(inputData)

        val entity = when (selectedSource) {
            CgmSource.JUGGLUCO -> juggluco.handleIntent(intent)
            CgmSource.XDRIP -> xdrip.handleIntent(intent)
            else -> {
                Log.d(TAG, "CGM source $selectedSource doesn't support intent parsing.")
                null
            }
        }

        return if (entity != null) {
            try {
                insertCgmUseCase(entity)
                Log.d(TAG, "Inserted CGM: $entity")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Insert failed", e)
                Result.retry()
            }
        } else {
            Log.w(TAG, "No CGM entity parsed or unsupported source.")
            Result.success()
        }
    }

    private fun reconstructIntent(data: Data): Intent {
        val intent = Intent(data.getString("action"))
        data.keyValueMap.forEach { (key, value) ->
            if (key != "action") {
                when (value) {
                    is Int -> intent.putExtra(key, value)
                    is Float -> intent.putExtra(key, value)
                    is Long -> intent.putExtra(key, value)
                    is String -> intent.putExtra(key, value)
                }
            }
        }
        return intent
    }
}
