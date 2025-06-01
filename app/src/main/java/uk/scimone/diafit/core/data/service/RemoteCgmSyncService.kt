package uk.scimone.diafit.core.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.koin.android.ext.android.inject
import uk.scimone.diafit.MainActivity
import uk.scimone.diafit.R
import uk.scimone.diafit.core.data.worker.RemoteCgmPoller

class RemoteCgmSyncService : Service() {

    private val poller: RemoteCgmPoller by inject()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "GLUCOSE_SYNC_CHANNEL"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("RemoteCgmSyncService", "onCreate called")
        poller.start()
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d("RemoteCgmSyncService", "Foreground service started")
    }

    override fun onDestroy() {
        Log.d("RemoteCgmSyncService", "onDestroy called")
        poller.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        Log.d("RemoteCgmSyncService", "Creating notification")

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("CGM Sync Service")
            .setContentText("Syncing CGM data in real-time")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // same as BroadcastIntentCgmSyncService
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
