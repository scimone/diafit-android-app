package uk.scimone.diafit.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.patterns.domain.BINS_PER_DAY
import uk.scimone.diafit.patterns.domain.DayGlucose
import uk.scimone.diafit.patterns.domain.DetectionInput
import uk.scimone.diafit.patterns.domain.PatternTone
import uk.scimone.diafit.patterns.domain.TrendDetector
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TrendDetectorTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 14)
    private val now = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** 29 days (oldest first); [level] gives the glucose by (age in days, bin) where age 1 = yesterday. */
    private fun detect(level: (Int, Int) -> Float): List<String> {
        val days = (1..29).reversed().map { age ->
            DayGlucose(today.minusDays(age.toLong()), FloatArray(BINS_PER_DAY) { level(age, it) })
        }
        return TrendDetector.detect(DetectionInput(days, GlucoseThresholds(low = 70, high = 180), now, zone)).map { it.card.id }
    }

    private fun wobble(age: Int, bin: Int, base: Float, amp: Float) = base + amp * (if ((bin / 12 + age) % 2 == 0) 1 else -1)

    @Test fun lessTimeInRangeAndHigherAverageAreFound() {
        val ids = detect { age, bin -> if (age <= 14) wobble(age, bin, 200f, 40f) else wobble(age, bin, 130f, 40f) }
        assertTrue("trend.tir" in ids); assertTrue("trend.mean" in ids)
    }

    @Test fun steadyPeriodsFindNothing() {
        assertEquals(emptyList<String>(), detect { age, bin -> wobble(age, bin, 120f, 30f) })
    }

    @Test fun morelowsAreFound() {
        val ids = detect { age, bin -> if (age <= 14 && age % 2 == 0 && bin in 20..40) 55f else 120f }
        assertTrue("trend.lows" in ids)
    }

    @Test fun fewerLowsIsFavourable() {
        val days = (1..29).reversed().map { age ->
            DayGlucose(today.minusDays(age.toLong()), FloatArray(BINS_PER_DAY) { bin -> if (age > 14 && age % 2 == 0 && bin in 20..40) 55f else 120f })
        }
        val c = TrendDetector.detect(DetectionInput(days, GlucoseThresholds(low = 70, high = 180), now, zone)).single { it.card.id == "trend.lows" }
        assertEquals(PatternTone.FAVORABLE, c.card.tone)
    }

    @Test fun tooFewCompleteDaysFindNothing() {
        val days = (1..5).map { DayGlucose(today.minusDays(it.toLong()), FloatArray(BINS_PER_DAY) { 120f }) }
        assertTrue(TrendDetector.detect(DetectionInput(days, GlucoseThresholds(low = 70, high = 180), now, zone)).isEmpty())
    }
}
