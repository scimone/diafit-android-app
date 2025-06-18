package uk.scimone.diafit.core.presentation.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import uk.scimone.diafit.core.data.worker.BolusBroadcastWorker
import uk.scimone.diafit.core.data.worker.CgmBroadcastWorker
import java.util.concurrent.TimeUnit

class HealthReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "HealthReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received intent with action: $action")

        var matched = false

        if (action in Intents.CGM_ACTIONS) {
            val workRequest = OneTimeWorkRequestBuilder<CgmBroadcastWorker>()
                .setInputData(intent.toWorkData())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Enqueued CGM worker for action: $action")
            matched = true
        }
        if (action in Intents.BOLUS_ACTIONS) {
            val workRequest = OneTimeWorkRequestBuilder<BolusBroadcastWorker>()
                .setInputData(intent.toWorkData())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "Enqueued Bolus worker for action: $action")
            matched = true
        }
        if (action in Intents.CARBS_ACTIONS) {
            // Dummy Carb worker (not implemented yet)
            Log.d(TAG, "Carb worker not implemented. Received CARBS action: $action")
            matched = true
        }
        if (!matched) {
            Log.w(TAG, "Unsupported action: $action")
        }
    }
}

// Helper extension
fun Intent.toWorkData(): Data {
    val builder = Data.Builder()
    builder.putString("action", action)
    extras?.keySet()?.forEach { key ->
        when (val value = extras?.get(key)) {
            is Int -> builder.putInt(key, value)
            is Float -> builder.putFloat(key, value)
            is Long -> builder.putLong(key, value)
            is Double -> builder.putFloat(key, value.toFloat()) // ✅ FIX
            is String -> builder.putString(key, value)
        }
    }
    return builder.build()
}
