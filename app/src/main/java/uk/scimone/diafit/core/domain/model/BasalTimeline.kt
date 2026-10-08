package uk.scimone.diafit.core.domain.model

import java.time.Instant
import java.time.ZoneId

/**
 * A stretch where the basal rate was constant: [scheduled] is what the profile in force says (null when no
 * profile switch is known for that time), [delivered] what actually ran (the loop's temp basal, else [scheduled]).
 */
data class BasalSegment(val startUtc: Long, val endUtc: Long, val delivered: Double, val scheduled: Double?)

/** A super micro bolus the loop gave: [units] at [timeUtc]. */
data class SmbMark(val timeUtc: Long, val units: Double, val count: Int = 1)

/** Total basal insulin (U) delivered within [fromUtc]..[toUtc]: every segment's rate times its length. */
fun List<BasalSegment>.deliveredUnits(fromUtc: Long, toUtc: Long): Double = sumOf { s ->
    val ms = (minOf(s.endUtc, toUtc) - maxOf(s.startUtc, fromUtc)).coerceAtLeast(0)
    s.delivered * ms / 3_600_000.0
}

/** SMBs closer together than this (chained) are drawn as one triangle. */
const val SMB_GROUP_GAP_MS = 15 * 60_000L
/** ...but one triangle never spans longer than this. */
const val SMB_GROUP_MAX_SPAN_MS = 30 * 60_000L

/** The SMBs of this list, nearby ones merged into one mark: summed dose, at the dose-weighted mean time. */
fun List<BolusEntity>.toSmbMarks(): List<SmbMark> {
    val groups = mutableListOf<MutableList<BolusEntity>>()
    filter { it.isSmb }.sortedBy { it.timestampUtc }.forEach { b ->
        val g = groups.lastOrNull()
        if (g != null && b.timestampUtc - g.last().timestampUtc <= SMB_GROUP_GAP_MS &&
            b.timestampUtc - g.first().timestampUtc <= SMB_GROUP_MAX_SPAN_MS) g += b else groups += mutableListOf(b)
    }
    return groups.map { g ->
        val units = g.sumOf { it.value.toDouble() }
        val time = if (units > 0) (g.sumOf { it.timestampUtc.toDouble() * it.value } / units).toLong() else g.first().timestampUtc
        SmbMark(time, units, g.size)
    }
}

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

/** The segment covering [timeUtc], or null where no basal is known. */
fun List<BasalSegment>.at(timeUtc: Long): BasalSegment? = firstOrNull { timeUtc >= it.startUtc && timeUtc < it.endUtc }

/** Linear interpolation of a time-sorted series such as [basalInsulinActivity]'s; null outside it. */
fun List<Pair<Long, Double>>.valueAt(timeUtc: Long): Double? {
    val i = indexOfFirst { it.first >= timeUtc }
    if (i < 0 || (i == 0 && first().first > timeUtc)) return null
    if (i == 0 || this[i].first == timeUtc) return this[i].second
    val (t0, v0) = this[i - 1]; val (t1, v1) = this[i]
    return v0 + (v1 - v0) * (timeUtc - t0).toDouble() / (t1 - t0)
}

/**
 * Insulin activity (U/min, the same unit as the bolus chart) of the basal alone: every minute's difference
 * between the delivered and the scheduled rate (a temp basal above the schedule adds insulin, below it takes
 * insulin away, so the curve can go negative) acts along the usual insulin curve. Minutes without a known
 * schedule count as no difference. SMBs ([smbs]) add their dose on top. Sampled every [stepMs] from [fromUtc] to [toUtc], which may lie after the
 * last segment to show the tail still acting.
 */
fun basalInsulinActivity(
    segments: List<BasalSegment>,
    fromUtc: Long,
    toUtc: Long,
    stepMs: Long = 5 * STEP_MS,
    smbs: List<SmbMark> = emptyList()
): List<Pair<Long, Double>> {
    val kernelMinutes = (4.5 * 60).toInt()
    val kernel = DoubleArray(kernelMinutes) { InsulinActivity.calculate(1.0, 0L, it * STEP_MS).activity }
    val deviations = HashMap<Long, Double>()   // minute index -> units/min delivered above (+) / below (-) the schedule
    segments.forEach { s ->
        val sched = s.scheduled ?: return@forEach
        val perMinute = (s.delivered - sched) / 60.0
        if (perMinute == 0.0) return@forEach
        // Segment times are not minute-aligned, so every minute is keyed by its index.
        for (minute in Math.floorDiv(s.startUtc, STEP_MS) until Math.floorDiv(s.endUtc - 1, STEP_MS) + 1) deviations[minute] = perMinute
    }
    val out = mutableListOf<Pair<Long, Double>>()
    var t = fromUtc
    while (t <= toUtc) {
        var a = 0.0
        for (k in 0 until kernelMinutes) {
            val d = deviations[Math.floorDiv(t, STEP_MS) - k] ?: continue
            a += d * kernel[k]
        }
        // SMBs are extra insulin on top of the schedule.
        smbs.forEach { smb ->
            val k = Math.floorDiv(t, STEP_MS) - Math.floorDiv(smb.timeUtc, STEP_MS)
            if (k in 0 until kernelMinutes) a += smb.units * kernel[k.toInt()]
        }
        out += t to a
        t += stepMs
    }
    return out
}
