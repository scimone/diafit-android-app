package uk.scimone.diafit.core.presentation.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import uk.scimone.diafit.core.data.worker.CgmInsertWorker
import java.util.concurrent.TimeUnit

class CgmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CgmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received intent with action: $action")

        // Only handle Juggluco for now
        if (action != Intents.JUGGLUCO_NEW_CGM) {
            Log.w(TAG, "Unsupported action: $action")
            return
        }

        val cgmValue = intent.getIntExtra("$action.mgdl", 0)
        val rate = intent.getFloatExtra("$action.Rate", 0f)
        val timestamp = intent.getLongExtra("$action.Time", 0L)

        if (timestamp == 0L) {
            Log.w(TAG, "Invalid timestamp in broadcast intent")
            return
        }

        val data = workDataOf(
            "cgm_value" to cgmValue,
            "rate" to rate,
            "timestamp" to timestamp
        )

        val workRequest = OneTimeWorkRequestBuilder<CgmInsertWorker>()
            .setInputData(data)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
