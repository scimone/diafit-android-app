package uk.scimone.diafit.core.presentation.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import uk.scimone.diafit.core.data.worker.CgmBroadcastWorker
import java.util.concurrent.TimeUnit

class CgmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CgmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received intent with action: $action")

        // Check if action matches one of the expected actions
        if (action != Intents.JUGGLUCO_NEW_CGM && action != Intents.XDRIP_NEW_CGM) {
            Log.w(TAG, "Unsupported action: $action")
            return
        }

        val workRequest = OneTimeWorkRequestBuilder<CgmBroadcastWorker>()
            .setInputData(intent.toWorkData())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
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
