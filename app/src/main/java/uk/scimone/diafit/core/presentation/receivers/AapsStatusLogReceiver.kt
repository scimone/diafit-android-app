package uk.scimone.diafit.core.presentation.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Temporary diagnostics: logs every extra of the AAPS status / NSClient treatment broadcasts so we can
 * see whether the temp basal rate is in them. Stores nothing. Read with `adb logcat AapsStatusLog:I '*:S'`.
 */
class AapsStatusLogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras
        val body = extras?.keySet()?.sorted()?.joinToString(" | ") { "$it=${extras.get(it)}" } ?: "(no extras)"
        Log.i(TAG, "${intent.action} :: $body")
    }

    private companion object {
        const val TAG = "AapsStatusLog"
    }
}
