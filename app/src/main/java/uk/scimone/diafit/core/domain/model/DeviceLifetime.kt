package uk.scimone.diafit.core.domain.model

import kotlin.math.abs

/**
 * The consumables whose age the Devices page tracks. Neither Nightscout nor AAPS reports an expiry date, so
 * expiry is the last matching change event plus a lifetime the user can set ([defaultHours] until then).
 * [PATCH] is not a setting: it stands for site + insulin when they are always changed together (patch pump).
 */
enum class DeviceKind(val label: String, val defaultHours: Int, private val pattern: Regex) {
    SENSOR("Sensor", 14 * 24, Regex("sensor (start|change)", RegexOption.IGNORE_CASE)),
    SITE("Infusion site", 72, Regex("site change|pod change|cannula", RegexOption.IGNORE_CASE)),
    INSULIN("Insulin / cartridge", 72, Regex("insulin change|reservoir|pod change", RegexOption.IGNORE_CASE)),
    BATTERY("Pump battery", 30 * 24, Regex("battery", RegexOption.IGNORE_CASE)),
    PATCH("Patch pump (site & insulin)", 72, Regex("site change|pod change|cannula|insulin change|reservoir", RegexOption.IGNORE_CASE));

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

/**
 * Newest change per kind from [events] (any order); a kind with no event has `changedAtUtc == null`.
 * Site and insulin become one [DeviceKind.PATCH] row when they are always changed together, and the battery
 * row is dropped unless a battery level is reported ([batteryReported]) or a battery change was logged.
 */
fun deviceAges(
    events: List<PumpEventEntity>, nowUtc: Long, lifetimes: DeviceLifetimes = emptyMap(), batteryReported: Boolean = false
): List<DeviceAge> {
    fun age(kind: DeviceKind, hours: Int = lifetimes.hours(kind)) = DeviceAge(
        kind, events.filter { kind.matches(it.eventType) && it.timestampUtc <= nowUtc }.maxOfOrNull { it.timestampUtc }, nowUtc, hours
    )
    val merged = siteAndInsulinChangeTogether(events)
    val kinds = DeviceKind.configurable.filter { !(merged && (it == DeviceKind.SITE || it == DeviceKind.INSULIN)) }
    val ages = kinds.map { age(it) }.toMutableList()
    if (merged) ages.add(1.coerceAtMost(ages.size), age(DeviceKind.PATCH, minOf(lifetimes.hours(DeviceKind.SITE), lifetimes.hours(DeviceKind.INSULIN))))
    return ages.filter { it.kind != DeviceKind.BATTERY || batteryReported || it.changedAtUtc != null }
}
