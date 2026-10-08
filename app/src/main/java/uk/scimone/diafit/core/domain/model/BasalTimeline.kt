package uk.scimone.diafit.core.domain.model

import java.time.Instant
import java.time.ZoneId

/**
 * A stretch where the basal rate was constant: [scheduled] is what the profile in force says (null when no
 * profile switch is known for that time), [delivered] what actually ran (the loop's temp basal, else [scheduled]).
 */
data class BasalSegment(val startUtc: Long, val endUtc: Long, val delivered: Double, val scheduled: Double?)

private const val STEP_MS = 60_000L

/**
 * Basal rate over [fromUtc]..[toUtc] as constant segments, sampled per minute.
 * The scheduled rate comes from the newest Profile Switch that carries a profile (its percentage and time
 * shift applied; the plain saved profile once a temporary switch has ended). A Temp Basal event overrides it
 * from its enact time for its duration, until a newer one replaces it. Times with neither are left out.
 */
fun buildBasalTimeline(
    switches: List<ProfileSwitch>,
    tempBasals: List<PumpEventEntity>,
    fromUtc: Long,
    toUtc: Long,
    zone: ZoneId = ZoneId.systemDefault()
): List<BasalSegment> {
    val sw = switches.filter { it.profile != null }.sortedBy { it.startUtc }
    val temps = tempBasals.filter { it.rate != null && it.durationMinutes != null }.sortedBy { it.timestampUtc }
    val out = mutableListOf<BasalSegment>()
    var t = fromUtc
    var swIdx = -1
    var tmpIdx = -1
    while (t < toUtc) {
        while (swIdx + 1 < sw.size && sw[swIdx + 1].startUtc <= t) swIdx++
        while (tmpIdx + 1 < temps.size && temps[tmpIdx + 1].timestampUtc <= t) tmpIdx++

        val scheduled = sw.getOrNull(swIdx)?.let { s ->
            val ended = s.endUtc?.let { it <= t } ?: false
            val profile = if (ended) s.profile else s.effectiveProfile()
            val secondOfDay = Instant.ofEpochMilli(t).atZone(zone).toLocalTime().toSecondOfDay()
            profile?.basal?.valueAt(secondOfDay)
        }
        val temp = temps.getOrNull(tmpIdx)?.takeIf { t < it.timestampUtc + (it.durationMinutes!! * 60_000).toLong() }
        val delivered = temp?.rate ?: scheduled
        if (delivered != null) {
            val end = minOf(t + STEP_MS, toUtc)
            val last = out.lastOrNull()
            if (last != null && last.endUtc == t && last.delivered == delivered && last.scheduled == scheduled) {
                out[out.lastIndex] = last.copy(endUtc = end)
            } else {
                out += BasalSegment(t, end, delivered, scheduled)
            }
        }
        t += STEP_MS
    }
    return out
}
