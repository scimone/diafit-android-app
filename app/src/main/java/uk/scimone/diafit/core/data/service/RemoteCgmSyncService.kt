package uk.scimone.diafit.core.data.service

import uk.scimone.diafit.R
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.koin.android.ext.android.inject
import uk.scimone.diafit.core.data.worker.CgmRemotePoller

class RemoteCgmSyncService : Service() {

    private val poller: CgmRemotePoller by inject()
    private val notificationUpdater: SyncNotificationUpdater by inject()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "GLUCOSE_SYNC_CHANNEL"
        private const val NOTIFICATION_ID = CgmServiceManager.CGM_SYNC_NOTIFICATION_ID
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("RemoteCgmSyncService", "onCreate called")
        poller.start()
        startForeground(NOTIFICATION_ID, createNotification())
        notificationUpdater.start()
        Log.d("RemoteCgmSyncService", "Foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ask the OS to recreate this service if it gets killed (e.g. by OEM
        // battery management overnight) instead of leaving CGM sync dead until
        // the app is manually reopened.
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("RemoteCgmSyncService", "onDestroy called")
        poller.stop()
        notificationUpdater.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        Log.d("RemoteCgmSyncService", "Creating notification")
        return SyncNotificationUpdater.build(this, getString(R.string.sync_cgm_title), getString(R.string.sync_cgm_text))
    }
}
