package uk.scimone.diafit

import org.junit.Assert.assertEquals
import uk.scimone.diafit.core.domain.model.TimeRange
import uk.scimone.diafit.core.domain.model.TimeRanges
import org.junit.Test

class TimeRangesTest {
    private fun r(a: Long, b: Long) = TimeRange(a, b)

    @Test fun mergeJoinsOverlappingAndTouching() {
        assertEquals(listOf(r(0, 30), r(40, 50)), TimeRanges.merge(listOf(r(20, 30), r(0, 10), r(10, 25), r(40, 50), r(60, 60))))
    }

    @Test fun subtractFindsGaps() {
        val gaps = TimeRanges.subtract(r(0, 100), listOf(r(10, 20), r(50, 60)))
        assertEquals(listOf(r(0, 10), r(20, 50), r(60, 100)), gaps)
    }

    @Test fun subtractNothingCoveredAndFullyCovered() {
        assertEquals(listOf(r(0, 100)), TimeRanges.subtract(r(0, 100), emptyList()))
        assertEquals(emptyList<TimeRange>(), TimeRanges.subtract(r(10, 20), listOf(r(0, 100))))
    }

    @Test fun subtractDropsGapsShorterThanMinimum() {
        assertEquals(listOf(r(20, 100)), TimeRanges.subtract(r(0, 100), listOf(r(5, 20)), minGap = 10))
    }

    @Test fun coveredBucketsNeedEnoughSamples() {
        val hour = 3_600_000L
        // 5 samples in hour 0, 1 in hour 1, 4 in hour 2.
        val ts = List(5) { it * 60_000L } + listOf(hour + 1) + List(4) { 2 * hour + it * 60_000L }
        assertEquals(listOf(r(0, hour), r(2 * hour, 3 * hour)), TimeRanges.coveredBuckets(ts, hour, 4))
    }
}
