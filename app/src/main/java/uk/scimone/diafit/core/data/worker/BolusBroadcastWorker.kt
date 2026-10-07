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

        val entities = when (selectedSource) {
            BolusSource.AAPS -> (aaps.handleIntent(intent) as? List<*>)?.filterIsInstance<BolusEntity>().orEmpty()
            else -> {
                Log.d(TAG, "Bolus source $selectedSource doesn't support intent parsing.")
                emptyList()
            }
        }

        if (entities.isEmpty()) {
            Log.w(TAG, "No bolus entity parsed or unsupported source.")
            return Result.success()
        }

        return try {
            entities.forEach {
                insertBolusUseCase(it)
                Log.d(TAG, "Inserted Bolus: $it")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Insert failed", e)
            Result.retry()
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
                    is Boolean -> intent.putExtra(key, value)
                }
            }
        }
        return intent
    }
}
