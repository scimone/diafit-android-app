package uk.scimone.diafit.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.patterns.domain.BINS_PER_DAY
import uk.scimone.diafit.patterns.domain.DayGlucose
import uk.scimone.diafit.patterns.domain.DetectionInput
import uk.scimone.diafit.patterns.domain.PatternCategory
import uk.scimone.diafit.patterns.domain.SleepDetector
import uk.scimone.diafit.patterns.domain.SleepNight
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SleepDetectorTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 14)
    private val now = today.atTime(23, 59).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val thresholds = GlucoseThresholds(low = 70, high = 180)

    /** 28 days; the day index d is "bad" (glucose 220 after waking) when [bad] says so, else 110. */
    private fun input(
        asleep: (Int) -> Int = { 450 }, awake: (Int) -> Double? = { null }, bedtime: (Int) -> Int = { 120 }, bad: (Int) -> Boolean
    ): DetectionInput {
        val days = (0 until 28).map { d ->
            DayGlucose(today.minusDays((27 - d).toLong()), FloatArray(BINS_PER_DAY) { if (bad(d) && it >= 96) 220f else 110f })
        }
        val nights = (0 until 27).map { d ->
            val date = today.minusDays((27 - d).toLong())
            val end = date.atTime(7, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
            SleepNight(date, end - asleep(d) * 60_000L, end, asleep(d), awake(d), bedtime(d))
        }
        return DetectionInput(days, thresholds, now, zone, nights = nights)
    }

    @Test fun shortNightsFollowedByWorseDaysAreFound() {
        val c = SleepDetector.detect(input(asleep = { if (it % 2 == 0) 330 else 480 }) { it % 2 == 0 }).single { it.card.id == "sleep.short" }
        assertEquals(PatternCategory.SLEEP, c.card.category)
        assertTrue(c.card.effectSize > 50)
        assertNotNull(c.card.cases.firstOrNull())
    }

    @Test fun noFindingWhenShortNightsAreNotWorse() {
        assertTrue(SleepDetector.detect(input(asleep = { if (it % 2 == 0) 330 else 480 }) { false }).isEmpty())
    }

    @Test fun nightsOnlyAnHourApartAreNotCalledShort() {
        assertTrue(SleepDetector.detect(input(asleep = { if (it % 2 == 0) 420 else 460 }) { it % 2 == 0 }).none { it.card.id == "sleep.short" })
    }

    @Test fun restlessNightsFollowedByWorseDaysAreFound() {
        val c = SleepDetector.detect(input(awake = { if (it % 2 == 0) 0.2 else 0.05 }) { it % 2 == 0 })
        assertTrue(c.any { it.card.id == "sleep.restless" })
    }

    @Test fun irregularBedtimesFollowedByWorseDaysAreFound() {
        val c = SleepDetector.detect(input(bedtime = { if (it % 3 == 0) 240 else 120 }) { it % 3 == 0 })
        assertTrue(c.any { it.card.id == "sleep.irregular" })
    }

    @Test fun tooFewNightsFindsNothing() {
        val i = input { it % 2 == 0 }
        assertTrue(SleepDetector.detect(DetectionInput(i.days, thresholds, now, zone, nights = i.nights.take(6))).isEmpty())
    }
}
