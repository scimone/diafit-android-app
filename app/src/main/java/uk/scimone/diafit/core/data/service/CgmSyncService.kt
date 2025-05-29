package uk.scimone.diafit.core.data.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import org.koin.android.ext.android.inject
import uk.scimone.diafit.MainActivity
import uk.scimone.diafit.core.data.worker.CgmPoller

class CgmSyncService : Service() {

    private val poller: CgmPoller by inject()

    override fun onCreate() {
        super.onCreate()
        poller.start()
        startForeground(NOTIF_ID, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        poller.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "GLUCOSE_SYNC_CHANNEL"
        val channelName = "Glucose Sync"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, channelId)
            .setContentTitle("DiaFit Sync Running")
            .setContentText("Syncing CGM data in real-time")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val NOTIF_ID = 1
    }
}
