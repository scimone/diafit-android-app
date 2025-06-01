package uk.scimone.diafit.core.data.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import uk.scimone.diafit.R
import uk.scimone.diafit.core.data.service.RemoteCgmSyncService.Companion
import uk.scimone.diafit.core.presentation.receivers.CgmReceiver

class BroadcastIntentCgmSyncService : Service() {

    private lateinit var cgmReceiver: CgmReceiver
    private var isReceiverRegistered = false

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "GLUCOSE_SYNC_CHANNEL"
        private const val NOTIFICATION_ID = 1
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        Log.d("BroadcastIntentCgmSyncService", "onCreate called")
        cgmReceiver = CgmReceiver()
        val filter = IntentFilter().apply {
            addAction("glucodata.Minute")
            addAction("com.eveningoutpost.dexdrip.BgEstimate")
        }

        if (Build.VERSION.SDK_INT >= 33) {
            // API 33+ version with flags
            registerReceiver(cgmReceiver, filter, null, null, android.content.Context.RECEIVER_EXPORTED)
        } else {
            // API < 33 fallback
            registerReceiver(cgmReceiver, filter)
        }
        isReceiverRegistered = true

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BroadcastIntentCgmSyncService", "Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        if (isReceiverRegistered) {
            unregisterReceiver(cgmReceiver)
            isReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        Log.d("BroadcastIntentCgmSyncService", "Creating notification")
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("CGM Sync Service")
            .setContentText("Listening for CGM broadcasts")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
