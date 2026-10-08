package uk.scimone.diafit.core.data.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource.AapsEventParser
import uk.scimone.diafit.core.domain.repository.PumpEventRepository
import uk.scimone.diafit.settings.domain.repository.SettingsRepository
import uk.scimone.diafit.settings.domain.model.Connector

/** Stores non-bolus, non-carb AAPS treatments (pod change, temp basal, ...) as [uk.scimone.diafit.core.domain.model.PumpEventEntity]. */
class PumpEventBroadcastWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val repository: PumpEventRepository by inject()
    private val settings: SettingsRepository by inject()

    override suspend fun doWork(): Result {
        if (Connector.AAPS !in settings.getEnabledConnectors()) return Result.success()
        val events = AapsEventParser.parse(reconstructIntent(inputData))
        return try {
            val inserted = events.count { repository.insert(it) }
            Log.d(TAG, "Pump events parsed=${events.size}, inserted=$inserted")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Insert failed", e)
            Result.retry()
        }
    }

    private fun reconstructIntent(data: Data): Intent {
        val intent = Intent(data.getString("action"))
        data.keyValueMap.forEach { (key, value) ->
            if (key != "action") when (value) {
                is Int -> intent.putExtra(key, value)
                is Float -> intent.putExtra(key, value)
                is Long -> intent.putExtra(key, value)
                is String -> intent.putExtra(key, value)
                is Boolean -> intent.putExtra(key, value)
            }
        }
        return intent
    }

    private companion object {
        const val TAG = "PumpEventWorker"
    }
}
