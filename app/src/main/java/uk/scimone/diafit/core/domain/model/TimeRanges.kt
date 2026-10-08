package uk.scimone.diafit.core.domain.model

/** A half-open time span `[start, end)` in epoch ms. */
data class TimeRange(val start: Long, val end: Long) {
    val length: Long get() = end - start
    val isEmpty: Boolean get() = end <= start
}

object TimeRanges {

    /** Sorts and joins overlapping or touching ranges; empty ones are dropped. */
    fun merge(ranges: List<TimeRange>): List<TimeRange> {
        val out = mutableListOf<TimeRange>()
        for (r in ranges.filter { !it.isEmpty }.sortedBy { it.start }) {
            val last = out.lastOrNull()
            if (last != null && r.start <= last.end) out[out.lastIndex] = TimeRange(last.start, maxOf(last.end, r.end))
            else out += r
        }
        return out
    }

    /** The parts of [target] not inside any of [covered], leaving out pieces shorter than [minGap]. */
    fun subtract(target: TimeRange, covered: List<TimeRange>, minGap: Long = 0L): List<TimeRange> {
        val gaps = mutableListOf<TimeRange>()
        var cursor = target.start
        for (c in merge(covered)) {
            if (c.end <= cursor) continue
            if (c.start >= target.end) break
            if (c.start > cursor) gaps += TimeRange(cursor, c.start)
            cursor = maxOf(cursor, c.end)
        }
        if (cursor < target.end) gaps += TimeRange(cursor, target.end)
        return gaps.filter { it.length >= minGap && !it.isEmpty }
    }

    /**
     * Spans of [bucketMs]-aligned buckets that hold at least [minPerBucket] of the [timestamps]: where a
     * continuous signal (glucose, heart rate) already has data.
     */
    fun coveredBuckets(timestamps: List<Long>, bucketMs: Long, minPerBucket: Int): List<TimeRange> {
        if (timestamps.isEmpty()) return emptyList()
        val counts = HashMap<Long, Int>()
        timestamps.forEach { counts.merge(Math.floorDiv(it, bucketMs), 1, Int::plus) }
        return merge(counts.filterValues { it >= minPerBucket }.keys.map { TimeRange(it * bucketMs, (it + 1) * bucketMs) })
    }
}
