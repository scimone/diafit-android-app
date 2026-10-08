package uk.scimone.diafit.core.data.worker

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uk.scimone.diafit.core.data.local.NotificationDao
import uk.scimone.diafit.core.data.nightscout.DeviceLifetimeStore
import uk.scimone.diafit.core.data.nightscout.DeviceStatusStore
import uk.scimone.diafit.core.domain.model.deviceAges
import uk.scimone.diafit.core.domain.model.expiryAlerts
import uk.scimone.diafit.devices.presentation.DeviceInputsSource
import uk.scimone.diafit.notifications.data.AppNotifier
import uk.scimone.diafit.notifications.data.NotificationChannelSpec
import uk.scimone.diafit.notifications.domain.DEVICES_LINK
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Raises the "sensor / pump part expires soon" alerts (see [expiryAlerts]): stores each one for the in-app bell
 * and pushes it to the phone. Runs every 15 minutes and whenever the app opens.
 */
class DeviceExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val inputsSource: DeviceInputsSource by inject()
    private val lifetimeStore: DeviceLifetimeStore by inject()
    private val statusStore: DeviceStatusStore by inject()
    private val dao: NotificationDao by inject()
    private val notifier: AppNotifier by inject()

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val status = statusStore.status.value
            val inputs = inputsSource.load(USER_ID, now)
            val ages = deviceAges(
                inputs.events, now, lifetimeStore.lifetimes.value,
                batteryReported = status?.let { it.pumpBatteryPercent != null || it.pumpBatteryVolt != null } == true,
                pumpType = inputs.pumpType
            )
            val format = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())
            for (alert in expiryAlerts(ages) { format.format(Date(it)) }) {
                notifier.raise(CHANNEL, alert.key, alert.title, alert.text, DEVICES_LINK, now)
            }
            dao.deleteOlderThan(now - 30L * 24 * 3_600_000)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Expiry check failed", e)
            Result.success() // the next run retries
        }
    }

    private companion object {
        const val TAG = "DeviceExpiry"
        val CHANNEL = NotificationChannelSpec(
            "DEVICE_EXPIRY_CHANNEL", "Device expiry",
            "Sensor, infusion site, insulin and battery nearing their expiry", NotificationManager.IMPORTANCE_HIGH
        )
        const val USER_ID = 1
    }
}

/** Schedules [DeviceExpiryWorker]: every 15 minutes, plus on demand. */
class DeviceExpiryScheduler(private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)

    fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            "device_expiry_periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DeviceExpiryWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    fun checkNow() {
        workManager.enqueueUniqueWork("device_expiry_now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<DeviceExpiryWorker>().build())
    }
}
