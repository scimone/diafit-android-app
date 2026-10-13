package uk.scimone.diafit.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.patterns.domain.BINS_PER_DAY
import uk.scimone.diafit.patterns.domain.DayGlucose
import uk.scimone.diafit.patterns.domain.DetectionInput
import uk.scimone.diafit.patterns.domain.PatternPipeline
import uk.scimone.diafit.patterns.domain.PatternTone
import uk.scimone.diafit.patterns.domain.TimeOfDayDetector
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TimeOfDayDetectorTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 14) // a Wednesday
    private val now = today.atTime(23, 59).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val thresholds = GlucoseThresholds(low = 70, high = 180)

    /** [count] days ending today; [value] gives mg/dL per (day index from oldest, bin), NaN for no reading. */
    private fun days(count: Int = 14, value: (Int, Int) -> Float): List<DayGlucose> =
        (0 until count).map { i ->
            DayGlucose(today.minusDays((count - 1 - i).toLong()), FloatArray(BINS_PER_DAY) { value(i, it) })
        }

    private fun detect(days: List<DayGlucose>) = TimeOfDayDetector.detect(DetectionInput(days, thresholds, now, zone))
    private fun ids(days: List<DayGlucose>) = detect(days).map { it.card.id }

    private fun flat(v: Float = 120f) = { _: Int, _: Int -> v }

    @Test fun flatInRangeDataGivesOnlyFavourablePatterns() {
        val found = detect(days(14, flat()))
        assertTrue(found.isNotEmpty())
        assertTrue(found.all { it.card.tone == PatternTone.FAVORABLE })
        assertTrue(found.all { it.card.detector == "steady" })
    }

    @Test fun recurringNightLowsAreFound() {
        val lowDays = setOf(1, 3, 5, 8, 10, 12)
        val d = days { day, bin -> if (day in lowDays && bin in 24..36) 60f else 120f }
        val low = detect(d).single { it.card.detector == "low" }
        assertEquals("tod.low.00-04", low.card.id)
        assertEquals(6, low.card.positiveCount)
        assertEquals(14, low.card.sampleSize)
        assertEquals(PatternTone.UNFAVORABLE, low.card.tone)
        assertTrue(low.card.cases.isNotEmpty())
        assertNotNull(low.card.miniGraph)
        // Each example shows the window with lead-in, and its key moment (the lowest point) lies inside the window.
        for (c in low.card.cases) {
            assertTrue(c.fromUtc < c.focusFromUtc && c.focusFromUtc < c.focusToUtc && c.focusToUtc <= c.toUtc)
            assertTrue(c.markUtc!! in c.focusFromUtc..c.focusToUtc)
            assertTrue(c.note.k == "pat_tod_low_case" && c.note.a[0] == "60")
        }
    }

    @Test fun neighbouringBlocksMergeIntoOneWindow() {
        val lowDays = setOf(1, 3, 5, 8, 10, 12)
        val d = days { day, bin -> if (day in lowDays && (bin in 24..36 || bin in 60..72)) 60f else 120f }
        val lows = detect(d).filter { it.card.detector == "low" }
        assertEquals(listOf("tod.low.00-08"), lows.map { it.card.id })
    }

    @Test fun aSingleBadDayIsNotAPattern() {
        val d = days { day, bin -> if (day == 6 && bin in 24..40) 50f else 120f }
        assertTrue(detect(d).none { it.card.detector == "low" })
    }

    @Test fun lowsOnlyInOneHalfOfThePeriodAreNotStable() {
        val d = days { day, bin -> if (day in 0..5 && bin in 24..36) 60f else 120f }
        assertTrue(detect(d).none { it.card.detector == "low" })
    }

    @Test fun daysWithTooLittleCoverageDoNotCount() {
        // Lows on 6 days, but those days only measured 25 % of the block: not enough measured to count them.
        val lowDays = setOf(1, 3, 5, 8, 10, 12)
        val d = days { day, bin ->
            when {
                day in lowDays && bin in 24..36 -> 60f
                day in lowDays && bin in 0..47 && bin % 4 != 0 && bin !in 24..36 -> Float.NaN
                else -> 120f
            }
        }
        assertTrue(detect(d).none { it.card.detector == "low" })
    }

    @Test fun tooFewDaysGivesNothing() {
        val d = days(5) { _, bin -> if (bin in 24..36) 60f else 120f }
        assertTrue(detect(d).isEmpty())
    }

    @Test fun recurringAfternoonHighsAreFound() {
        val d = days { _, bin -> if (bin in 144..191) 230f else 120f } // 12:00–16:00
        val high = detect(d).single { it.card.detector == "high" }
        assertEquals("tod.high.12-16", high.card.id)
        assertEquals(PatternTone.UNFAVORABLE, high.card.tone)
        assertEquals(14, high.card.positiveCount)
    }

    @Test fun dawnRiseIsFound() {
        val d = days { _, bin ->
            when {
                bin < 42 -> 100f
                bin < 78 -> 100f + 40f * (bin - 42) / 36f
                else -> 140f
            }
        }
        val dawn = detect(d).single { it.card.detector == "dawn" }
        assertTrue(dawn.card.effectSize >= 30)
    }

    @Test fun weekendDifferenceIsFound() {
        val d = days(28) { day, _ ->
            val date = today.minusDays((27 - day).toLong())
            if (date.dayOfWeek.value >= 6) 220f else 120f
        }
        val w = detect(d).single { it.card.detector == "weekend" }
        assertEquals(PatternTone.UNFAVORABLE, w.card.tone)
        assertNotNull(w.card.baselineLabel)
    }

    @Test fun noWeekendFindingWhenWeekendsAreLikeWeekdays() {
        assertTrue(detect(days(28, flat())).none { it.card.detector == "weekend" })
    }

    @Test fun pipelineGivesExactlyThreeTipsAndCapsTheList() {
        val lowDays = setOf(1, 3, 5, 8, 10, 12)
        val d = days { day, bin -> if (day in lowDays && bin in 24..36) 60f else 120f }
        val cards = PatternPipeline.finalize(detect(d))
        assertTrue(cards.isNotEmpty())
        assertTrue(cards.all { it.tips.size == 3 })
        assertTrue(cards.size <= PatternPipeline.MAX_PATTERNS)
        assertEquals(cards.sortedByDescending { it.priority }, cards)
    }

    @Test fun existingTipsAreKeptBetweenScans() {
        val lowDays = setOf(1, 3, 5, 8, 10, 12)
        val d = days { day, bin -> if (day in lowDays && bin in 24..36) 60f else 120f }
        val first = PatternPipeline.finalize(detect(d))
        val second = PatternPipeline.finalize(detect(d), existingTips = first.associate { it.id to it.tips }, tipsInUse = first.flatMap { it.tips }.toSet())
        assertEquals(first.map { it.tips }, second.map { it.tips })
    }
}
