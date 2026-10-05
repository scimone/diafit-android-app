package uk.scimone.diafit.core.data.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import uk.scimone.diafit.R

class BroadcastIntentHealthSyncService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "GLUCOSE_SYNC_CHANNEL"
        private const val NOTIFICATION_ID = 1
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BroadcastIntentHealthSyncService", "Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        Log.d("BroadcastIntentHealthSyncService", "Creating notification")
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Health Sync Service")
            .setContentText("Listening for health data broadcasts")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
