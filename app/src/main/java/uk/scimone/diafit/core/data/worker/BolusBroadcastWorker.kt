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
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.repository.syncsource.IntentHealthSyncSource
import uk.scimone.diafit.core.domain.usecase.InsertBolusUseCase
import uk.scimone.diafit.settings.domain.model.BolusSource
import uk.scimone.diafit.settings.domain.usecase.GetBolusSourceUseCase

class BolusBroadcastWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val insertBolusUseCase: InsertBolusUseCase by inject()
    private val getBolusSourceUseCase: GetBolusSourceUseCase by inject()

    private val aaps: IntentHealthSyncSource by inject(named("AAPS"))

    companion object {
        private const val TAG = "BolusInsertWorker"
    }

    override suspend fun doWork(): Result {
        val selectedSource = getBolusSourceUseCase()

        val intent = reconstructIntent(inputData)

        val entity = when (selectedSource) {
            BolusSource.AAPS -> aaps.handleIntent(intent)
            else -> {
                Log.d(TAG, "Bolus source $selectedSource doesn't support intent parsing.")
                null
            }
        }

        return if (entity is BolusEntity) {
            try {
                insertBolusUseCase(entity)
                Log.d(TAG, "Inserted Bolus: $entity")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Insert failed", e)
                Result.retry()
            }
        } else {
            Log.w(TAG, "No bolus entity parsed or unsupported source.")
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
