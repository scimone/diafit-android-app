package uk.scimone.diafit.core.domain.model

import kotlin.math.abs

/** A stretch of the day where a schedule value differs between two profiles. */
data class ScheduleChange(val startSeconds: Int, val endSeconds: Int, val before: Double, val after: Double)

/**
 * Where [after] differs from [before] over the day, as the fewest contiguous stretches (neighbouring stretches
 * with the same before/after values are merged). Empty when either schedule is empty or they are equal.
 */
fun diffSchedules(before: List<ProfileStep>, after: List<ProfileStep>): List<ScheduleChange> {
    if (before.isEmpty() || after.isEmpty()) return emptyList()
    val points = (before.map { it.startSeconds } + after.map { it.startSeconds } + 0 + AapsProfile.SECONDS_PER_DAY)
        .filter { it in 0..AapsProfile.SECONDS_PER_DAY }.distinct().sorted()
    val changes = mutableListOf<ScheduleChange>()
    points.zipWithNext().forEach { (start, end) ->
        val b = before.valueAt(start) ?: return@forEach
        val a = after.valueAt(start) ?: return@forEach
        if (abs(a - b) < 1e-6) return@forEach
        val last = changes.lastOrNull()
        if (last != null && last.endSeconds == start && abs(last.before - b) < 1e-6 && abs(last.after - a) < 1e-6) {
            changes[changes.lastIndex] = last.copy(endSeconds = end)
        } else {
            changes += ScheduleChange(start, end, b, a)
        }
    }
    return changes
}

/** The profile as AAPS runs it during this switch: time shift, then percentage. */
fun ProfileSwitch.effectiveProfile(): AapsProfile? = profile?.atTimeShift(timeShiftHours)?.atPercentage(percentage)

/**
 * What AAPS was running just before [switchStartUtc], given the previous Profile Switch: its effective profile
 * (percentage and time shift), or the plain saved profile when that switch was temporary and had already ended.
 */
fun ProfileSwitch.effectiveProfileAt(switchStartUtc: Long): AapsProfile? {
    val p = profile ?: return null
    val ended = endUtc?.let { it <= switchStartUtc } ?: false
    return if (ended) p else effectiveProfile()
}

/** Moves every schedule [hours] later in the day (AAPS time shift); negative moves earlier. */
fun AapsProfile.atTimeShift(hours: Int): AapsProfile {
    if (hours % 24 == 0) return this
    val shift = ((hours * 3600) % AapsProfile.SECONDS_PER_DAY + AapsProfile.SECONDS_PER_DAY) % AapsProfile.SECONDS_PER_DAY
    fun List<ProfileStep>.shifted(): List<ProfileStep> {
        if (isEmpty()) return this
        val starts = (map { (it.startSeconds + shift) % AapsProfile.SECONDS_PER_DAY } + 0).distinct().sorted()
        return starts.map { st -> ProfileStep(st, valueAt(((st - shift) + AapsProfile.SECONDS_PER_DAY) % AapsProfile.SECONDS_PER_DAY)!!) }
    }
    return copy(basal = basal.shifted(), isf = isf.shifted(), carbRatio = carbRatio.shifted(), targetLow = targetLow.shifted(), targetHigh = targetHigh.shifted())
}

/**
 * The part of the day a switch is in force, from [startSeconds] (local time of day) for [lengthSeconds].
 * A switch of a day or longer covers the whole day (its schedule just repeats).
 */
data class DayWindow(val startSeconds: Int, val lengthSeconds: Int) {
    val isFullDay: Boolean get() = lengthSeconds >= AapsProfile.SECONDS_PER_DAY
    /** Intervals within 0..86400 (two when the window wraps past midnight). */
    val intervals: List<Pair<Int, Int>>
        get() {
            val day = AapsProfile.SECONDS_PER_DAY
            if (isFullDay) return listOf(0 to day)
            val end = startSeconds + lengthSeconds
            return if (end <= day) listOf(startSeconds to end) else listOf(startSeconds to day, 0 to end - day)
        }
}

/** Keeps only the parts of [changes] inside [window]. */
fun clipChanges(changes: List<ScheduleChange>, window: DayWindow): List<ScheduleChange> =
    window.intervals.flatMap { (ws, we) ->
        changes.mapNotNull { c ->
            val s = maxOf(c.startSeconds, ws)
            val e = minOf(c.endSeconds, we)
            if (e > s) c.copy(startSeconds = s, endSeconds = e) else null
        }
    }.sortedBy { it.startSeconds }

/** Basal insulin in units delivered by [steps] over [lengthSeconds] starting at [startSeconds] of the day (repeats daily). */
fun basalInsulin(steps: List<ProfileStep>, startSeconds: Int, lengthSeconds: Int): Double {
    var sum = 0.0
    var m = 0
    while (m * 60 < lengthSeconds) {
        val rate = steps.valueAt((startSeconds + m * 60) % AapsProfile.SECONDS_PER_DAY) ?: 0.0
        sum += rate / 60.0
        m++
    }
    return sum
}
