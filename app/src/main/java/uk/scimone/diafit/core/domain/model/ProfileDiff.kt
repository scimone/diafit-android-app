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

/**
 * What AAPS was running just before [switchStartUtc], given the previous Profile Switch: its profile at its
 * percentage, or at 100 % when that switch was temporary and had already ended.
 */
fun ProfileSwitch.effectiveProfileAt(switchStartUtc: Long): AapsProfile? {
    val p = profile ?: return null
    val ended = endUtc?.let { it <= switchStartUtc } ?: false
    return p.atPercentage(if (ended) 100 else percentage)
}
