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
import android.view.View
import android.widget.RemoteViews
import uk.scimone.diafit.core.data.nightscout.DeviceLifetimeStore
import uk.scimone.diafit.core.data.nightscout.DeviceStatusStore
import uk.scimone.diafit.core.domain.model.DeviceKind
import uk.scimone.diafit.core.domain.model.WarnItem
import uk.scimone.diafit.core.domain.model.WarnLevel
import uk.scimone.diafit.core.domain.model.warnItems
import uk.scimone.diafit.core.domain.model.deviceAges
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

/** One entry of the bottom row: what to print and whether it is the initial (yellow) or urgent (red) warning. */
class ExpiryText(val kind: DeviceKind, val text: String, val urgent: Boolean)

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
    private val statusStore: DeviceStatusStore,
    private val warnStore: uk.scimone.diafit.core.data.nightscout.DeviceWarnStore
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
        val title = syncNotificationTitle(latest, readings.getOrNull(readings.size - 2))
        val warning = expiryLine(now)
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
        NotificationManagerCompat.from(context).notify(CgmServiceManager.CGM_SYNC_NOTIFICATION_ID, build(context, title, text, warning))
    }

    /** The warning texts of what is about to run out, or null when nothing is. */
    private suspend fun expiryLine(now: Long): List<ExpiryText>? {
        val items = try {
            val status = statusStore.status.value
            val inputs = deviceInputs.load(USER_ID, now)
            warnItems(
                deviceAges(
                    inputs.events, now, lifetimeStore.lifetimes.value,
                    batteryReported = status?.let { it.pumpBatteryPercent != null || it.pumpBatteryVolt != null } == true,
                    pumpType = inputs.pumpType
                ),
                status?.reservoirUnits, warnStore.thresholds.value
            )
        } catch (e: Exception) { Log.e(TAG, "Expiry lookup failed", e); return null }
        return items.map { item ->
            val body = when (item.kind) {
                DeviceKind.INSULIN -> item.units?.let { "${it.toInt()} U" } ?: shortLeft(item.remainingMs ?: 0, now)
                DeviceKind.BATTERY -> context.getString(R.string.sync_battery_days, ((item.ageMs ?: 0) / (24 * HOUR)).toInt())
                else -> shortLeft(item.remainingMs ?: 0, now)
            }
            ExpiryText(item.kind, body, item.level == WarnLevel.URGENT)
        }.takeIf { it.isNotEmpty() }
    }

    /** "20h · Wed 09:15": time left, then when it expires (weekday only when not today). */
    private fun shortLeft(ms: Long, now: Long): String {
        val left = when {
            ms <= 0 -> "0h"
            ms < HOUR -> "${ms / 60_000}m"
            else -> "${ms / HOUR}h"
        }
        val at = Instant.ofEpochMilli(now + ms).atZone(ZoneId.systemDefault())
        val sameDay = at.toLocalDate() == Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val pattern = if (sameDay) "HH:mm" else "EEE HH:mm"
        return "$left (${DateTimeFormatter.ofPattern(pattern).format(at)})"
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
        /** Rough characters that fit on one notification row next to their icons. */
        private const val ROW_BUDGET = 40
        const val CHANNEL_ID = "GLUCOSE_SYNC_CHANNEL"

        private fun views(context: Context, title: String, text: String, warning: List<ExpiryText>?, expanded: Boolean) =
            RemoteViews(context.packageName, R.layout.notification_sync).apply {
                setTextViewText(R.id.sync_title, title)
                setTextViewText(R.id.sync_text, text)
                setViewVisibility(R.id.sync_warning, View.GONE)
                setViewVisibility(R.id.sync_warning2, View.GONE)
                // No flow layout in a notification: fill rows greedily by an estimated width, so items share a row when they fit.
                val rows = mutableListOf<MutableList<ExpiryText>>()
                var used = 0
                warning?.forEach { item ->
                    val cost = item.text.length + 5
                    if (rows.isEmpty() || used + cost > ROW_BUDGET) { rows.add(mutableListOf()); used = 0 }
                    rows.last().add(item); used += cost
                }
                rows.take(2).forEachIndexed { i, row ->
                    val container = if (i == 0) R.id.sync_warning else R.id.sync_warning2
                    setViewVisibility(container, View.VISIBLE)
                    row.forEach { item ->
                        val color = if (item.urgent) Color.rgb(0xE5, 0x39, 0x35) else Color.rgb(0xE0, 0x9A, 0x00)
                        val v = RemoteViews(context.packageName, R.layout.notification_sync_item)
                        v.setImageViewResource(R.id.item_icon, when (item.kind) {
                            DeviceKind.SENSOR -> R.drawable.ic_sensor
                            DeviceKind.INSULIN -> R.drawable.ic_vial
                            DeviceKind.BATTERY -> R.drawable.ic_battery
                            else -> R.drawable.ic_insulin_pump
                        })
                        v.setInt(R.id.item_icon, "setColorFilter", color)
                        v.setTextViewText(R.id.item_text, item.text)
                        v.setTextColor(R.id.item_text, color)
                        addView(container, v)
                    }
                }
                if (expanded) setInt(R.id.sync_text, "setMaxLines", 6)
            }

        fun build(context: Context, title: String, text: String, warning: List<ExpiryText>? = null): Notification {
            val pendingIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(views(context, title, text, warning, false))
                .setCustomBigContentView(views(context, title, text, warning, true))
                .setSmallIcon(R.drawable.ic_stat_diafit)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build()
        }
    }
}
