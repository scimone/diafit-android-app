package uk.scimone.diafit.core.domain.model

import androidx.annotation.StringRes
import uk.scimone.diafit.R
import kotlin.math.abs

/**
 * The consumables whose age the Devices page tracks. Neither Nightscout nor AAPS reports an expiry date, so
 * expiry is the last matching change event plus a lifetime the user can set ([defaultHours] until then).
 * [PATCH] is not a setting: it stands for site + insulin when they are always changed together (patch pump).
 */
enum class DeviceKind(@StringRes val labelRes: Int, val defaultHours: Int, private val pattern: Regex) {
    SENSOR(R.string.device_kind_sensor, 14 * 24, Regex("sensor (start|change)", RegexOption.IGNORE_CASE)),
    SITE(R.string.device_kind_site, 72, Regex("site change|pod change|cannula", RegexOption.IGNORE_CASE)),
    INSULIN(R.string.device_kind_insulin, 72, Regex("insulin change|reservoir|pod change", RegexOption.IGNORE_CASE)),
    BATTERY(R.string.device_kind_battery, 30 * 24, Regex("battery", RegexOption.IGNORE_CASE)),
    PATCH(R.string.device_kind_patch, 72, Regex("site change|pod change|cannula|insulin change|reservoir", RegexOption.IGNORE_CASE));

    fun matches(eventType: String) = pattern.containsMatchIn(eventType)

    companion object {
        /** The kinds the user sets a lifetime for. */
        val configurable = listOf(SENSOR, SITE, INSULIN, BATTERY)
    }
}

typealias DeviceLifetimes = Map<DeviceKind, Int>

fun DeviceLifetimes.hours(kind: DeviceKind): Int = this[kind] ?: kind.defaultHours

data class DeviceAge(val kind: DeviceKind, val changedAtUtc: Long?, val nowUtc: Long, val lifetimeHours: Int) {
    val expiresAtUtc: Long? get() = changedAtUtc?.plus(lifetimeHours * 3_600_000L)
    val ageMs: Long? get() = changedAtUtc?.let { nowUtc - it }
    /** Positive: time left; negative: overdue. */
    val remainingMs: Long? get() = expiresAtUtc?.let { it - nowUtc }
    /** 0..1+ of the lifetime used. */
    val fractionUsed: Float? get() = ageMs?.let { it.toFloat() / (lifetimeHours * 3_600_000L) }
}

private const val SAME_TIME_MS = 2 * 60_000L

/** True when site and insulin changes both exist and every one of them has the other within 2 minutes (patch pump). */
fun siteAndInsulinChangeTogether(events: List<PumpEventEntity>): Boolean {
    val site = events.filter { DeviceKind.SITE.matches(it.eventType) }.map { it.timestampUtc }
    val insulin = events.filter { DeviceKind.INSULIN.matches(it.eventType) }.map { it.timestampUtc }
    if (site.isEmpty() || insulin.isEmpty()) return false
    return site.all { s -> insulin.any { abs(it - s) <= SAME_TIME_MS } } && insulin.all { i -> site.any { abs(it - i) <= SAME_TIME_MS } }
}

private val PATCH_PUMP_NAME = Regex("omnipod|eopatch|eoflow|medtrum", RegexOption.IGNORE_CASE)
private val PATCH_PUMP_TYPE = Regex("\"pumpType\"\\s*:\\s*\"[^\"]*(omnipod|eopatch|eoflow|medtrum)", RegexOption.IGNORE_CASE)

/** What the Devices page and the lifetime settings show: a patch pump has one row for site + insulin. */
data class DeviceLayout(val patch: Boolean, val showBattery: Boolean)

/**
 * A patch pump is recognised by the `pumpType` AAPS puts on its treatments (OMNIPOD_DASH, ...): the latest bolus's
 * ([pumpType], stored on `BolusEntity`) or a stored temp basal's raw JSON; failing that, by site and insulin
 * always being changed together.
 * The battery only exists when a level is reported ([batteryReported]) or a battery change was logged.
 */
fun deviceLayout(events: List<PumpEventEntity>, batteryReported: Boolean, pumpType: String? = null): DeviceLayout = DeviceLayout(
    patch = (pumpType != null && PATCH_PUMP_NAME.containsMatchIn(pumpType)) || events.any { PATCH_PUMP_TYPE.containsMatchIn(it.rawJson) } || siteAndInsulinChangeTogether(events),
    showBattery = batteryReported || events.any { DeviceKind.BATTERY.matches(it.eventType) }
)

/**
 * Newest change per kind from [events] (any order); a kind with no event has `changedAtUtc == null`.
 * Site and insulin become one [DeviceKind.PATCH] row when they are always changed together, and the battery
 * row is dropped unless a battery level is reported ([batteryReported]) or a battery change was logged.
 */
fun deviceAges(
    events: List<PumpEventEntity>, nowUtc: Long, lifetimes: DeviceLifetimes = emptyMap(), batteryReported: Boolean = false, pumpType: String? = null
): List<DeviceAge> {
    fun age(kind: DeviceKind, hours: Int = lifetimes.hours(kind)) = DeviceAge(
        kind, events.filter { kind.matches(it.eventType) && it.timestampUtc <= nowUtc }.maxOfOrNull { it.timestampUtc }, nowUtc, hours
    )
    val merged = deviceLayout(events, batteryReported, pumpType).patch
    val kinds = DeviceKind.configurable.filter { !(merged && (it == DeviceKind.SITE || it == DeviceKind.INSULIN)) }
    val ages = kinds.map { age(it) }.toMutableList()
    if (merged) ages.add(1.coerceAtMost(ages.size), age(DeviceKind.PATCH, minOf(lifetimes.hours(DeviceKind.SITE), lifetimes.hours(DeviceKind.INSULIN))))
    return ages.filter { it.kind != DeviceKind.BATTERY || deviceLayout(events, batteryReported, pumpType).showBattery }
}

/** Warning levels for one consumable: [initial] (yellow) and [urgent] (red). Hours before expiry, or units left for insulin. */
data class WarnLevels(val initial: Int, val urgent: Int)

/**
 * User-set warning thresholds. [DeviceKind.PATCH] shares the site's. Defaults: sensor 24 h / 1 h, site and patch
 * 10 h / 1 h, insulin 20 U / 5 U left, battery 72 h / 24 h before its lifetime ends.
 */
class WarnThresholds(private val levels: Map<DeviceKind, WarnLevels> = emptyMap()) {
    fun of(kind: DeviceKind): WarnLevels = levels[if (kind == DeviceKind.PATCH) DeviceKind.SITE else kind] ?: defaults.getValue(if (kind == DeviceKind.PATCH) DeviceKind.SITE else kind)

    companion object {
        val defaults = mapOf(
            DeviceKind.SENSOR to WarnLevels(24, 1), DeviceKind.SITE to WarnLevels(10, 1),
            DeviceKind.INSULIN to WarnLevels(20, 5), DeviceKind.BATTERY to WarnLevels(72, 24)
        )
    }
}

enum class WarnLevel { INITIAL, URGENT }

/** One thing about to run out, for the bottom row of the sync notification. */
data class WarnItem(val kind: DeviceKind, val level: WarnLevel, val remainingMs: Long?, val units: Double? = null, val ageMs: Long? = null)

/**
 * What the sync notification warns about: sensor, site (or patch pump), insulin (units left when [reservoirUnits]
 * is known, else by time) and battery, each once it passes its initial threshold; urgent past the second one.
 */
fun warnItems(ages: List<DeviceAge>, reservoirUnits: Double?, thresholds: WarnThresholds = WarnThresholds()): List<WarnItem> = ages.mapNotNull { age ->
    val lv = thresholds.of(age.kind)
    if (age.kind == DeviceKind.INSULIN && reservoirUnits != null) {
        return@mapNotNull if (reservoirUnits <= lv.initial) WarnItem(age.kind, if (reservoirUnits <= lv.urgent) WarnLevel.URGENT else WarnLevel.INITIAL, age.remainingMs, units = reservoirUnits) else null
    }
    val left = age.remainingMs ?: return@mapNotNull null
    if (left >= lv.initial * 3_600_000L) null
    else WarnItem(age.kind, if (left < lv.urgent * 3_600_000L) WarnLevel.URGENT else WarnLevel.INITIAL, left, ageMs = age.ageMs)
}
