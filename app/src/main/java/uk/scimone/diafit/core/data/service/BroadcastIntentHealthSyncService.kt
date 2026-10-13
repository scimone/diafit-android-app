package uk.scimone.diafit.core.data.service

import uk.scimone.diafit.R
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.koin.android.ext.android.inject

class BroadcastIntentHealthSyncService : Service() {

    private val notificationUpdater: SyncNotificationUpdater by inject()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "GLUCOSE_SYNC_CHANNEL"
        private const val NOTIFICATION_ID = CgmServiceManager.CGM_SYNC_NOTIFICATION_ID
    }

    // Broadcasts are handled by the manifest-declared HealthReceiver, which works
    // regardless of whether this service is running. This service only keeps the
    // app alive in the foreground while an intent-based source is selected; it must
    // NOT register a second HealthReceiver here, or every broadcast gets processed twice.
    override fun onCreate() {
        super.onCreate()
        Log.d("BroadcastIntentHealthSyncService", "onCreate called")
        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )
        notificationUpdater.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BroadcastIntentHealthSyncService", "Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        notificationUpdater.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        Log.d("BroadcastIntentHealthSyncService", "Creating notification")
        return SyncNotificationUpdater.build(this, getString(R.string.sync_health_title), getString(R.string.sync_health_text))
    }
}
