package uk.scimone.diafit.core.data.healthconnect

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord

enum class HealthConnectAvailability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

/** The Health Connect permissions Diafit asks for, grouped by what the user is switching on. */
object HealthConnectPermissions {
    /** Heart rate, steps, sleep and exercise: the activity charts. */
    val activity: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )

    /** Blood glucose, when Health Connect is chosen as the CGM source. */
    val glucose: Set<String> = setOf(HealthPermission.getReadPermission(BloodGlucoseRecord::class))

    /** Lets the periodic import (and the CGM sync service) read while Diafit isn't on screen. */
    /** Reading data from before the first permission grant plus 30 days (needed to backfill older history). */
    const val HISTORY = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    const val BACKGROUND = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
}

/** Entry point to the Health Connect client: availability, which permissions are granted, revoking. */
class HealthConnectManager(private val context: Context) {

    fun availability(): HealthConnectAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
        else -> HealthConnectAvailability.UNAVAILABLE
    }

    val isAvailable: Boolean get() = availability() == HealthConnectAvailability.AVAILABLE

    /** Throws if Health Connect isn't available; check [isAvailable] first. */
    val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun grantedPermissions(): Set<String> =
        if (isAvailable) client.permissionController.getGrantedPermissions() else emptySet()

    suspend fun revokeAll() {
        if (isAvailable) client.permissionController.revokeAllPermissions()
    }

    /** Opens the Play Store page of Health Connect (to install or update it). */
    fun storeIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding")
    ).apply { setPackage("com.android.vending"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /** Health Connect's own settings screen (manage permissions and data). */
    fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    }
}
