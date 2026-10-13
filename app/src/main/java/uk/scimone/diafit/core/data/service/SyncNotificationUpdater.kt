package uk.scimone.diafit.core.data.service

import uk.scimone.diafit.core.presentation.formatStamp
import uk.scimone.diafit.core.presentation.AppStrings
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uk.scimone.diafit.MainActivity
import uk.scimone.diafit.R
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.repository.ActivityRepository
import uk.scimone.diafit.core.domain.repository.BolusRepository
import uk.scimone.diafit.core.domain.repository.CgmRepository
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.patterns.domain.GlucoseSituation
import uk.scimone.diafit.patterns.domain.SituationInput
import uk.scimone.diafit.settings.domain.usecase.GetTargetRangeUseCase
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import uk.scimone.diafit.core.data.nightscout.DeviceLifetimeStore
import uk.scimone.diafit.core.data.nightscout.DeviceStatusStore
import uk.scimone.diafit.core.domain.model.ExpiryWarning
import uk.scimone.diafit.core.domain.model.deviceAges
import uk.scimone.diafit.core.domain.model.expiryWarning
import uk.scimone.diafit.devices.presentation.DeviceInputsSource
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Instant

/** Title of the sync notification: "123 △+3 mg/dL" (delta to the reading before; omitted if that one is old). */
fun syncNotificationTitle(latest: CgmEntity, previous: CgmEntity?): String {
    val delta = previous?.takeIf { latest.timestamp - it.timestamp <= 15 * 60_000L }
        ?.let { latest.valueMgdl - it.valueMgdl }
    val d = when {
        delta == null -> ""
        delta > 0 -> " △+$delta"
        delta < 0 -> " △-${-delta}"
        else -> " △0"
    }
    return "${latest.valueMgdl}$d mg/dL"
}

/**
 * Keeps the permanent sync notification (shared by both foreground services) up to date: the title shows the
 * latest glucose and its change, the text the current situation from [GlucoseSituation].
 */
class SyncNotificationUpdater(
    private val context: Context,
    private val cgm: CgmRepository,
    private val boluses: BolusRepository,
    private val meals: MealRepository,
    private val activity: ActivityRepository,
    private val targetRange: GetTargetRangeUseCase,
    private val deviceInputs: DeviceInputsSource,
    private val lifetimeStore: DeviceLifetimeStore,
    private val statusStore: DeviceStatusStore
) {

    private var scope: CoroutineScope? = null
    private var history: List<CgmEntity> = emptyList()
    private var historyLoadedAt = 0L

    fun start() {
        if (scope?.isActive == true) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch {
            val ticker = flow { while (true) { emit(Unit); delay(60_000) } }
            val inserts = cgm.getLatestCgm(USER_ID).map { }.catch { }
            merge(ticker, inserts).conflate().collect {
                try { update() } catch (e: Exception) { Log.e(TAG, "Notification update failed", e) }
                delay(2_000) // coalesce bursts of inserts
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun update() {
        val now = System.currentTimeMillis()
        val readings = cgm.getEntriesBetween(now - 26 * HOUR, now, USER_ID)
        val latest = readings.lastOrNull() ?: return
        val title = withExpiry(syncNotificationTitle(latest, readings.getOrNull(readings.size - 2)), now)
        val ageMin = ((now - latest.timestamp) / 60_000).toInt()

        val text = if (ageMin > 15) context.getString(R.string.sync_last_reading, ageMin) else {
            val range = targetRange()
            val since = now - 8 * HOUR
            val data = activity.getBetween(since, now, USER_ID)
            val input = SituationInput(
                nowUtc = now, zone = ZoneId.systemDefault(), readings = readings,
                boluses = boluses.getBolusBetween(since, now, USER_ID),
                meals = meals.getUnmergedSince(USER_ID, since),
                exercise = data.exercise, heartRate = activity.getHeartRateBetween(now - 24 * HOUR, now, USER_ID),
                asleepNow = data.sleep.any { it.startUtc <= now && it.endUtc >= now },
                history = loadHistory(now), targetLow = range.lowerBound, targetHigh = range.upperBound
            )
            val situation = GlucoseSituation.classify(input) ?: GlucoseSituation.trendFallback(input)
            situation?.text() ?: AppStrings.byName("sit_updated_at", listOf(formatStamp(latest.timestamp, "Hm")))
        }
        NotificationManagerCompat.from(context).notify(CgmServiceManager.CGM_SYNC_NOTIFICATION_ID, build(context, title, text))
    }

    /** Appends the red "Sensor 20h · Pump 6h" warning (only what is about to expire) to the first notification row. */
    private suspend fun withExpiry(title: String, now: Long): CharSequence {
        val warning = try {
            val status = statusStore.status.value
            val inputs = deviceInputs.load(USER_ID, now)
            expiryWarning(deviceAges(
                inputs.events, now, lifetimeStore.lifetimes.value,
                batteryReported = status?.let { it.pumpBatteryPercent != null || it.pumpBatteryVolt != null } == true,
                pumpType = inputs.pumpType
            ))
        } catch (e: Exception) { Log.e(TAG, "Expiry lookup failed", e); return title }
        val parts = listOfNotNull(
            warning.sensorMs?.let { context.getString(R.string.sync_expiry_sensor, shortLeft(it)) },
            warning.pumpMs?.let { context.getString(R.string.sync_expiry_pump, shortLeft(it)) }
        )
        if (parts.isEmpty()) return title
        val sb = SpannableStringBuilder(title).append("  ")
        val start = sb.length
        sb.append(parts.joinToString(" · "))
        sb.setSpan(ForegroundColorSpan(Color.RED), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }

    private fun shortLeft(ms: Long): String = when {
        ms <= 0 -> "0h"
        ms < HOUR -> "${ms / 60_000}m"
        else -> "${ms / HOUR}h"
    }

    /** About 13 days of CGM thinned to one reading per 5 min, reloaded hourly (only the recurrence rules need it). */
    private suspend fun loadHistory(now: Long): List<CgmEntity> {
        if (now - historyLoadedAt > HOUR || history.isEmpty()) {
            var lastKept = 0L
            history = cgm.getEntriesBetween(now - 14 * 24 * HOUR, now - 24 * HOUR, USER_ID)
                .filter { if (it.timestamp - lastKept >= 5 * 60_000L) { lastKept = it.timestamp; true } else false }
            historyLoadedAt = now
        }
        return history
    }

    companion object {
        private const val TAG = "SyncNotification"
        private const val USER_ID = 1
        private const val HOUR = 3_600_000L
        const val CHANNEL_ID = "GLUCOSE_SYNC_CHANNEL"

        fun build(context: Context, title: CharSequence, text: String): Notification {
            val pendingIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(R.drawable.ic_stat_diafit)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build()
        }
    }
}
