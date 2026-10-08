package uk.scimone.diafit.core.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An in-app notification (also pushed to the phone). [dedupeKey] makes sure the same alert is only raised once.
 * [link] says what tapping it opens (`devices`, or `patterns:` + highlighted patterns); null = the Devices page,
 * which is all that older rows link to.
 */
@Entity(indices = [Index(value = ["dedupeKey"], unique = true), Index(value = ["timestampUtc"])])
data class AppNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestampUtc: Long,
    val title: String,
    val text: String,
    val dedupeKey: String,
    val isRead: Boolean = false,
    val link: String? = null
)

/** Which expiry alert to raise for one consumable, if any. */
data class ExpiryAlert(val key: String, val title: String, val text: String)

private const val HOUR_MS = 3_600_000L

/** Hours before expiry at which to notify, largest first: sensor 24 h + 1 h, pump parts 10 h + 1 h. */
fun alertHours(kind: DeviceKind): List<Int> = if (kind == DeviceKind.SENSOR) listOf(24, 1) else listOf(10, 1)

/**
 * The alert due now for each consumable: the smallest threshold already crossed (so a late check raises "1 hour"
 * rather than a stale "10 hours"), only while it hasn't expired yet. The key repeats for the same change, so
 * storing it with a unique index raises each alert once.
 */
fun expiryAlerts(ages: List<DeviceAge>, formatTime: (Long) -> String): List<ExpiryAlert> = ages.mapNotNull { age ->
    val changed = age.changedAtUtc ?: return@mapNotNull null
    val remaining = age.remainingMs ?: return@mapNotNull null
    if (remaining <= 0) return@mapNotNull null
    val threshold = alertHours(age.kind).sorted().firstOrNull { remaining <= it * HOUR_MS } ?: return@mapNotNull null
    val left = if (threshold == 1) "less than 1 hour" else "about ${(remaining + HOUR_MS / 2) / HOUR_MS} hours"
    ExpiryAlert(
        key = "${age.kind.name}-$changed-$threshold",
        title = "${age.kind.label} expires in $left",
        text = "Due ${formatTime(age.expiresAtUtc!!)}. Change it and log the change so the countdown restarts."
    )
}
