package uk.scimone.diafit.core.domain.model

/**
 * The consumables whose age the Devices page tracks. Neither Nightscout nor AAPS reports an expiry date, so
 * expiry is the last matching change event plus a typical [lifetimeHours].
 */
enum class DeviceKind(val label: String, val lifetimeHours: Int, private val pattern: Regex) {
    SENSOR("Sensor", 14 * 24, Regex("sensor (start|change)", RegexOption.IGNORE_CASE)),
    SITE("Infusion site", 72, Regex("site change|pod change|cannula", RegexOption.IGNORE_CASE)),
    INSULIN("Insulin / cartridge", 72, Regex("insulin change|reservoir|pod change", RegexOption.IGNORE_CASE)),
    BATTERY("Pump battery", 30 * 24, Regex("battery", RegexOption.IGNORE_CASE));

    fun matches(eventType: String) = pattern.containsMatchIn(eventType)
}

data class DeviceAge(val kind: DeviceKind, val changedAtUtc: Long?, val nowUtc: Long) {
    val expiresAtUtc: Long? get() = changedAtUtc?.plus(kind.lifetimeHours * 3_600_000L)
    val ageMs: Long? get() = changedAtUtc?.let { nowUtc - it }
    /** Positive: time left; negative: overdue. */
    val remainingMs: Long? get() = expiresAtUtc?.let { it - nowUtc }
    /** 0..1+ of the lifetime used. */
    val fractionUsed: Float? get() = ageMs?.let { it.toFloat() / (kind.lifetimeHours * 3_600_000L) }
}

/** Newest change per kind from [events] (any order). A kind with no event in the list has `changedAtUtc == null`. */
fun deviceAges(events: List<PumpEventEntity>, nowUtc: Long): List<DeviceAge> =
    DeviceKind.values().map { kind ->
        DeviceAge(kind, events.filter { kind.matches(it.eventType) && it.timestampUtc <= nowUtc }.maxOfOrNull { it.timestampUtc }, nowUtc)
    }
