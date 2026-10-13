package uk.scimone.diafit.core.domain.model

import androidx.room.ColumnInfo
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
    val link: String? = null,
    /** Removed by the user: hidden, but kept so the [dedupeKey] still stops the same alert from being raised again. */
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false
)

/**
 * Which expiry alert to raise for one consumable. [leftHours] is 0 for "under 1 h". The bell stores [storedTitle] /
 * [storedText] (language-neutral, rendered when shown by `renderNotification`), so alerts follow the app language.
 */
data class ExpiryAlert(val key: String, val kind: DeviceKind, val leftHours: Int, val expiresAtUtc: Long, val units: Int? = null) {
    val storedTitle get() = if (units != null) "$STORED_LOW_UNITS|$units" else "$STORED_EXPIRY|${kind.name}|$leftHours"
    val storedText get() = if (units != null) STORED_LOW_UNITS else "$STORED_DUE|$expiresAtUtc"
}

const val STORED_LOW_UNITS = "@lowunits"

const val STORED_EXPIRY = "@expiry"
const val STORED_DUE = "@due"

private const val HOUR_MS = 3_600_000L

/**
 * The alert due now for each consumable: the smallest threshold already crossed (so a late check raises "1 hour"
 * rather than a stale "10 hours"), only while it hasn't expired yet. The key repeats for the same change, so
 * storing it with a unique index raises each alert once. Insulin alerts by units left when [reservoirUnits] is known.
 */
fun expiryAlerts(ages: List<DeviceAge>, thresholds: WarnThresholds = WarnThresholds(), reservoirUnits: Double? = null): List<ExpiryAlert> = ages.mapNotNull { age ->
    val lv = thresholds.of(age.kind)
    if (age.kind == DeviceKind.INSULIN && reservoirUnits != null) {
        val threshold = listOf(lv.urgent, lv.initial).sorted().firstOrNull { reservoirUnits <= it } ?: return@mapNotNull null
        return@mapNotNull ExpiryAlert("INSULIN-units-${age.changedAtUtc ?: 0}-$threshold", age.kind, 0, 0L, units = reservoirUnits.toInt())
    }
    val changed = age.changedAtUtc ?: return@mapNotNull null
    val remaining = age.remainingMs ?: return@mapNotNull null
    if (remaining <= 0) return@mapNotNull null
    val threshold = listOf(lv.initial, lv.urgent).sorted().firstOrNull { remaining <= it * HOUR_MS } ?: return@mapNotNull null
    ExpiryAlert(
        key = "${age.kind.name}-$changed-$threshold",
        kind = age.kind,
        leftHours = if (remaining <= HOUR_MS) 0 else ((remaining + HOUR_MS / 2) / HOUR_MS).toInt(),
        expiresAtUtc = age.expiresAtUtc!!
    )
}

private val LEGACY_TITLE = Regex("^(.*?)(?: \\(.*?\\))? expires in (?:less than 1 hour|about (\\d+) hours)$")
private val LEGACY_TEXT = Regex("^Due (.*?)\\.(?: .*)?$")

/**
 * Notifications stored before the short naming (\"Patch pump (site & insulin) expires in about 9 hours\" / \"Due Sat 10 Oct
 * 15:25. Change it and log the change so the countdown restarts.\") shown in the current convention. Anything else,
 * including notifications already in the new form, is returned unchanged.
 */
fun tidyNotificationText(title: String, text: String): Pair<String, String> {
    val t = LEGACY_TITLE.matchEntire(title) ?: return title to text
    val d = LEGACY_TEXT.matchEntire(text) ?: return title to text
    val hours = t.groupValues[2]
    return "${t.groupValues[1]} expires in ${if (hours.isEmpty()) "under 1 h" else "$hours h"}" to "Devices · due ${d.groupValues[1]}"
}
