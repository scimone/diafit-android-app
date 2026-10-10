package uk.scimone.diafit.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import uk.scimone.diafit.stats.data.offsetSegments
import uk.scimone.diafit.stats.domain.GlucoseDay
import uk.scimone.diafit.stats.domain.SleepNight
import uk.scimone.diafit.stats.domain.StatsDay
import uk.scimone.diafit.stats.domain.StatsMetric
import uk.scimone.diafit.stats.domain.StatsPeriod
import uk.scimone.diafit.stats.domain.StatsWindow
import java.time.LocalDate
import java.time.ZoneId

class StatsTest {
    private val today = LocalDate.of(2026, 10, 10) // a Saturday

    @Test fun weekRunsMondayToSunday() {
        val w = StatsWindow.of(StatsPeriod.WEEK, today)
        assertEquals(LocalDate.of(2026, 10, 5), w.first)
        assertEquals(LocalDate.of(2026, 10, 12), w.lastExclusive)
        assertEquals(7, w.buckets.size)
        assertEquals(LocalDate.of(2026, 9, 28), StatsWindow.of(StatsPeriod.WEEK, today, -1).first)
    }

    @Test fun monthAndYearBuckets() {
        val m = StatsWindow.of(StatsPeriod.MONTH, today, -1) // September: 30 days
        assertEquals(30, m.buckets.size)
        val y = StatsWindow.of(StatsPeriod.YEAR, today)
        assertEquals(12, y.buckets.size)
        assertEquals(31, y.buckets[0].days.size)
        assertEquals(LocalDate.of(2026, 1, 1), y.first)
    }

    @Test fun dayWindowLabels() {
        assertEquals("Today", StatsWindow.of(StatsPeriod.DAY, today).label(today))
        assertEquals("Yesterday", StatsWindow.of(StatsPeriod.DAY, today, -1).label(today))
    }

    @Test fun barAveragesOnlyDaysWithData() {
        val days = listOf(
            StatsDay(today, steps = 4000), StatsDay(today.plusDays(1), steps = null), StatsDay(today.plusDays(2), steps = 8000)
        )
        assertEquals(6000.0, StatsMetric.STEPS.average(days)!!, 0.001)
        assertEquals(12000.0, StatsMetric.STEPS.total(days)!!, 0.001)
        assertEquals(2, StatsMetric.STEPS.daysWithData(days))
        assertNull(StatsMetric.STEPS.average(listOf(StatsDay(today))))
    }

    @Test fun glucoseSegmentsAreZoneShares() {
        val g = GlucoseDay.from(n = 100, sum = 12000, sumSq = 12000L * 120 + 100 * 400, below = 10, above = 20)!!
        assertEquals(0.1, g.belowShare, 1e-9)
        assertEquals(0.7, g.inRangeShare, 1e-9)
        assertEquals(0.2, g.aboveShare, 1e-9)
        assertEquals(120.0, g.meanMgdl, 1e-9)
        assertEquals(20.0, g.sdMgdl, 1e-6)
        assertEquals(3.31 + 0.02392 * 120, g.gmi, 1e-9)
        val seg = StatsMetric.GLUCOSE.segments(StatsDay(today, glucose = g))!!
        assertEquals(70.0, seg[1], 1e-9)
        assertEquals(70.0, StatsMetric.GLUCOSE.primary(StatsDay(today, glucose = g))!!, 1e-9)
    }

    @Test fun caloriesSplitIntoActiveAndResting() {
        val seg = StatsMetric.CALORIES.segments(StatsDay(today, totalKcal = 2400.0, activeKcal = 600.0))!!
        assertEquals(listOf(600.0, 1800.0), seg)
        assertEquals(2400.0, StatsMetric.CALORIES.primary(StatsDay(today, totalKcal = 2400.0, activeKcal = 600.0))!!, 1e-9)
        assertNull(StatsMetric.CALORIES.segments(StatsDay(today, activeKcal = 600.0)))
    }

    @Test fun sleepSegmentsAndScheduleAxis() {
        val hour = 3_600_000L
        val night = SleepNight(deepMs = hour, remMs = 2 * hour, lightMs = 4 * hour, awakeMs = hour, bedMinutes = 330, wakeMinutes = 780)
        val day = StatsDay(today, sleep = night)
        assertEquals(listOf(1.0, 2.0, 4.0), StatsMetric.SLEEP.segments(day))
        assertEquals(7.0, StatsMetric.SLEEP.primary(day)!!, 1e-9)
        assertEquals(listOf(330.0, 780.0), StatsMetric.SLEEP_SCHEDULE.segments(day))
        // 330 min after 18:00 is 23:30, 780 is 07:00.
        assertEquals(23 * 60 + 30, SleepNight.axisToClock(330))
        assertEquals(7 * 60, SleepNight.axisToClock(780))
        assertEquals(330, SleepNight.clockToAxis(23 * 60 + 30))
    }

    @Test fun offsetSegmentsSplitAtDaylightSavingChange() {
        val zone = ZoneId.of("Europe/London")
        val start = LocalDate.of(2026, 10, 20).atStartOfDay(zone).toInstant().toEpochMilli() // clocks went back on 25 Oct
        val end = LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val a = LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val segs = offsetSegments(zone, minOf(a, start), end)
        assertEquals(2, segs.size)
        assertEquals(3_600_000L, segs[0].third)
        assertEquals(0L, segs[1].third)
        assertEquals(segs[0].second, segs[1].first)
        assertNotNull(segs.last())
    }
}
