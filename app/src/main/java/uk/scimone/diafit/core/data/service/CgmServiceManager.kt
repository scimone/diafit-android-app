package uk.scimone.diafit.core.data.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import uk.scimone.diafit.settings.domain.model.CgmSource

class CgmServiceManager(
    private val context: Context
) {

    companion object {
        private const val TAG = "CgmServiceManager"

        // Shared with RemoteCgmSyncService and BroadcastIntentHealthSyncService: the two
        // are mutually exclusive (stopAll() always stops the other before starting one),
        // so they can safely share one notification id/channel.
        const val CGM_SYNC_NOTIFICATION_ID = 1
    }

    // In-memory only: reset to null whenever the app process restarts, which is exactly
    // when we also can't be sure the service survived, so start() below correctly treats
    // a fresh process as "not running yet" rather than skipping a needed start.
    @Volatile
    private var currentSource: CgmSource? = null

    fun start(cgmSource: CgmSource) {
        if (currentSource == cgmSource) {
            Log.d(TAG, "Already running for $cgmSource, skipping restart")
            return
        }

        Log.d(TAG, "Starting CGM service for $cgmSource")
        stopAll()

        when (cgmSource) {
            // Both poll once a minute from the foreground service.
            CgmSource.NIGHTSCOUT, CgmSource.HEALTH_CONNECT -> ContextCompat.startForegroundService(
                context,
                Intent(context, RemoteCgmSyncService::class.java)
            )
            CgmSource.JUGGLUCO, CgmSource.XDRIP -> ContextCompat.startForegroundService(
                context,
                Intent(context, BroadcastIntentHealthSyncService::class.java)
            )
        }
        currentSource = cgmSource
    }

    /**
     * Used by the periodic watchdog to recover from the OS/OEM killing the foreground
     * service without telling this manager (common on MIUI/HyperOS overnight). Only
     * forces a restart when the service's notification isn't actually showing, so it
     * doesn't cause a restart blip on every check.
     */
    fun ensureRunning(cgmSource: CgmSource) {
        if (isServiceNotificationActive()) {
            Log.d(TAG, "Watchdog check: CGM sync notification still active for $cgmSource")
            return
        }
        Log.w(TAG, "Watchdog check: CGM sync notification missing, restarting $cgmSource")
        currentSource = null
        start(cgmSource)
    }

    private fun isServiceNotificationActive(): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.any { it.id == CGM_SYNC_NOTIFICATION_ID }
    }

    private fun stopAll() {
        context.stopService(Intent().setClass(context, RemoteCgmSyncService::class.java))
        context.stopService(Intent().setClass(context, BroadcastIntentHealthSyncService::class.java))
    }

    fun stop(source: CgmSource) {
        if (currentSource == source) currentSource = null
        when (source) {
            CgmSource.NIGHTSCOUT, CgmSource.HEALTH_CONNECT -> context.stopService(Intent().setClass(context, RemoteCgmSyncService::class.java))
            CgmSource.JUGGLUCO, CgmSource.XDRIP -> context.stopService(Intent().setClass(context, BroadcastIntentHealthSyncService::class.java))
        }
    }
}
