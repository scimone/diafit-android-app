package uk.scimone.diafit.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import uk.scimone.diafit.stats.data.offsetSegments
import uk.scimone.diafit.stats.domain.GlucoseDay
import uk.scimone.diafit.stats.domain.SleepNight
import uk.scimone.diafit.stats.domain.StatsCards
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

    @Test fun glucoseMetricsAreSeparateCards() {
        val g = GlucoseDay.from(n = 100, sum = 12000, sumSq = 12000L * 120 + 100 * 400, below = 10, above = 20)!!
        assertEquals(0.1, g.belowShare, 1e-9)
        assertEquals(120.0, g.meanMgdl, 1e-9)
        assertEquals(20.0, g.sdMgdl, 1e-6)
        val day = StatsDay(today, glucose = g)
        assertEquals(listOf(10.0), StatsMetric.GLUCOSE_BELOW.segments(day))
        assertEquals(listOf(70.0), StatsMetric.GLUCOSE_IN_RANGE.segments(day))
        assertEquals(listOf(20.0), StatsMetric.GLUCOSE_ABOVE.segments(day))
        assertEquals(listOf(120.0), StatsMetric.GLUCOSE_MEAN.segments(day))
        assertEquals(3.31 + 0.02392 * 120, StatsMetric.GLUCOSE_A1C.primary(day)!!, 1e-9)
        assertNull(StatsMetric.GLUCOSE_MEAN.segments(StatsDay(today)))
    }

    @Test fun a1cOnlyOnMonthAndYear() {
        assertEquals(false, StatsMetric.GLUCOSE_A1C in StatsCards.bars(StatsPeriod.WEEK))
        assertEquals(true, StatsMetric.GLUCOSE_A1C in StatsCards.bars(StatsPeriod.MONTH))
        assertEquals(true, StatsMetric.GLUCOSE_A1C in StatsCards.bars(StatsPeriod.YEAR))
        assertEquals(StatsMetric.GLUCOSE_IN_RANGE, StatsCards.bars(StatsPeriod.WEEK).first())
    }

    @Test fun macrosAndCaloriesSplit() {
        val day = StatsDay(today, carbs = 100.0, protein = 30.0, fat = null, totalKcal = 2400.0, activeKcal = 600.0)
        assertEquals(listOf(30.0), StatsMetric.PROTEIN.segments(day))
        assertEquals(listOf(0.0), StatsMetric.FAT.segments(day))
        assertNull(StatsMetric.PROTEIN.segments(StatsDay(today)))
        assertEquals(listOf(2400.0), StatsMetric.CALORIES.segments(day))
        assertEquals(listOf(600.0), StatsMetric.ACTIVE_CALORIES.segments(day))
    }

    @Test fun sleepStagesScheduleAndAxis() {
        val hour = 3_600_000L
        val night = SleepNight(deepMs = hour, remMs = 2 * hour, lightMs = 4 * hour, awakeMs = hour, bedMinutes = 330, wakeMinutes = 780)
        val day = StatsDay(today, sleep = night)
        assertEquals(listOf(7.0), StatsMetric.SLEEP.segments(day))
        assertEquals(listOf(1.0), StatsMetric.DEEP_SLEEP.segments(day))
        assertEquals(listOf(2.0), StatsMetric.REM_SLEEP.segments(day))
        assertEquals(listOf(330.0, 780.0), StatsMetric.SLEEP_SCHEDULE.segments(day))
        // A source without stages (everything "light") has no deep/REM card.
        val noStages = StatsDay(today, sleep = night.copy(deepMs = 0, remMs = 0))
        assertNull(StatsMetric.DEEP_SLEEP.segments(noStages))
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
