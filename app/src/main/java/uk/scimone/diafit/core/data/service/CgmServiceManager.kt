package uk.scimone.diafit.core.data.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import uk.scimone.diafit.settings.domain.model.CgmSource

class CgmServiceManager(
    private val context: Context
) {

    fun start(source: CgmSource) {
        Log.d("CgmServiceManager", "Starting BroadcastIntentCgmSyncService")
        stopAll()

        when (source) {
            CgmSource.NIGHTSCOUT -> ContextCompat.startForegroundService(
                context,
                Intent(context, RemoteCgmSyncService::class.java)
            )
            CgmSource.JUGGLUCO, CgmSource.XDRIP -> ContextCompat.startForegroundService(
                context,
                Intent(context, BroadcastIntentCgmSyncService::class.java)
            )
            else -> { /* No service started */ }
        }
    }

    private fun stopAll() {
        context.stopService(Intent().setClass(context, RemoteCgmSyncService::class.java))
        context.stopService(Intent().setClass(context, BroadcastIntentCgmSyncService::class.java))
    }

    fun stop(source: CgmSource) {
        when (source) {
            CgmSource.NIGHTSCOUT -> context.stopService(Intent().setClass(context, RemoteCgmSyncService::class.java))
            CgmSource.JUGGLUCO, CgmSource.XDRIP -> context.stopService(Intent().setClass(context, BroadcastIntentCgmSyncService::class.java))
            else -> { }
        }
    }
}
