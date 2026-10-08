package uk.scimone.diafit.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.notifications.domain.parsePatternsLink
import uk.scimone.diafit.notifications.domain.patternsLink
import uk.scimone.diafit.patterns.domain.AgpPattern
import uk.scimone.diafit.patterns.domain.HourSpan
import uk.scimone.diafit.patterns.domain.PATTERN_REALERT_MS
import uk.scimone.diafit.patterns.domain.PatternAgp
import uk.scimone.diafit.patterns.domain.PatternTone
import uk.scimone.diafit.patterns.domain.detectAgpPatternTexts
import uk.scimone.diafit.patterns.domain.newPatternAlerts
import uk.scimone.diafit.patterns.domain.round1
import kotlin.math.exp
import kotlin.math.pow

/** Reference vectors from `patterns.md` §5, generated with the backend's own code. */
class PatternDetectionTest {

    private fun profile(m: (Double) -> Double, s: (Double) -> Double): PatternAgp {
        fun arr(k: Int) = DoubleArray(PatternAgp.BINS) { i -> val h = i / 12.0; round1(m(h) + k * s(h)) }
        return PatternAgp(arr(-2), arr(-1), arr(0), arr(1), arr(2))
    }

    @Test
    fun vectorA() {
        val agp = profile({ h ->
            120 + 60 * exp(-(h - 8.5).pow(2) / 1.5) + 50 * exp(-(h - 20).pow(2) / 2) - 55 * exp(-(h - 3).pow(2) / 1.0)
        }, { 15.0 })
        assertEquals(
            listOf(
                "Consistent hypoglycemia during night",
                "Post-breakfast glucose spike",
                "Dawn phenomenon detected",
                "Extended post-breakfast elevation",
                "Well-controlled post-dinner glucose response"
            ),
            detectAgpPatternTexts(agp)
        )
    }

    @Test
    fun vectorB() {
        val agp = profile({ h -> 200 + 70 * exp(-(h - 13).pow(2) / 0.5) }, { 35.0 })
        val periods = listOf("night", "morning", "noon", "afternoon", "evening")
        val expected = periods.map { "Elevated glucose during $it" } +
            listOf("Post-lunch glucose spike", "Elevated fasting glucose levels") +
            periods.map { "High glucose variability during $it" } +
            listOf("Overall glucose trending high") +
            periods.map { "Inconsistent glucose patterns during $it" }
        assertEquals(expected, detectAgpPatternTexts(agp))
    }

    @Test
    fun vectorC() {
        val agp = profile({ 110.0 }, { 8.0 })
        val periods = listOf("night", "morning", "noon", "afternoon", "evening")
        val expected = periods.map { "Tight glucose control during $it" } +
            listOf("Excellent overall glucose control") +
            periods.map { "Consistent glucose patterns during $it" }
        assertEquals(expected, detectAgpPatternTexts(agp))
    }

    @Test
    fun highlightsFollowTheDashboardKeys() {
        assertEquals(HourSpan(15, 18), AgpPattern("High glucose variability during afternoon").highlight)
        assertEquals(HourSpan(11, 15), AgpPattern("Tight glucose control during noon").highlight)
        assertEquals(HourSpan(7, 10), AgpPattern("Rapid post-breakfast glucose spike").highlight)
        assertEquals(HourSpan(3, 7), AgpPattern("Dawn phenomenon detected").highlight)
        assertEquals(HourSpan(5, 7), AgpPattern("Elevated fasting glucose levels").highlight)
        assertEquals(HourSpan(22, 7), AgpPattern("Consistent hypoglycemia during night").highlight)
        assertNull(AgpPattern("Overall glucose trending high").highlight)
    }

    @Test
    fun tones() {
        assertEquals(PatternTone.DANGER, AgpPattern("Sporadic, very dangerous hypoglycemia during night").tone)
        assertEquals(PatternTone.CONCERN, AgpPattern("Post-lunch glucose spike").tone)
        assertEquals(PatternTone.GOOD, AgpPattern("Consistent glucose patterns during evening").tone)
        assertEquals(PatternTone.CONCERN, AgpPattern("Inconsistent glucose patterns during evening").tone)
        assertEquals(PatternTone.GOOD, AgpPattern("Well-controlled post-dinner glucose response").tone)
    }

    @Test
    fun alertsOnlyNewConcerns() {
        val now = 1_000_000_000_000L
        val current = listOf("Post-lunch glucose spike", "Tight glucose control during noon", "Dawn phenomenon detected").map(::AgpPattern)
        // First check: every concern is new, good news isn't alerted.
        assertEquals(listOf("Post-lunch glucose spike", "Dawn phenomenon detected"), newPatternAlerts(current, emptySet(), emptyMap(), now).map { it.text })
        // Still there on the next check: nothing.
        assertTrue(newPatternAlerts(current, current.map { it.text }.toSet(), emptyMap(), now).isEmpty())
        // Came back soon after its alert: suppressed; after the re-alert period: alerted again.
        val alerted = mapOf("Post-lunch glucose spike" to now - 1000)
        val prev = setOf("Dawn phenomenon detected")
        assertTrue(newPatternAlerts(current, prev, alerted, now).isEmpty())
        assertEquals(1, newPatternAlerts(current, prev, alerted, now + PATTERN_REALERT_MS).size)
    }

    @Test
    fun linkRoundTrip() {
        val texts = listOf("Sporadic, very dangerous hypoglycemia during night", "Post-lunch glucose spike")
        assertEquals(texts, parsePatternsLink(patternsLink(texts)))
        assertNull(parsePatternsLink("devices"))
        assertNull(parsePatternsLink(null))
    }

    @Test
    fun buildNeedsEnoughDays() {
        val minutes = IntArray(288 * 4) { (it % 288) * 5 }
        val values = IntArray(minutes.size) { 120 }
        assertNull(PatternAgp.build(minutes, values, dayCount = 4))
    }

    @Test
    fun buildFromFlatReadings() {
        // 7 days of 5-min readings: every bin pools 5 bins x 7 = 35 readings.
        val minutes = IntArray(288 * 7) { (it % 288) * 5 }
        val values = IntArray(minutes.size) { i -> 100 + (i / 288) * 5 } // day d reads 100 + 5d
        val agp = PatternAgp.build(minutes, values, dayCount = 7)!!
        assertEquals(115.0, agp.p50[100], 0.001)
        // 35 sorted values, five each of 100, 105, ..., 130; numpy-style linear interpolation between ranks.
        assertEquals(100.0, agp.p10[0], 0.001) // rank 3.4
        assertEquals(125.0, agp.p75[0], 0.001) // rank 25.5
        assertTrue(agp.p90[0] > agp.p75[0] && agp.p75[0] > agp.p50[0])
    }

    @Test
    fun buildFailsWithAHoleInTheDay() {
        // Nothing between 10:00 and 13:00, wider than the pooling window can bridge.
        val minutes = (0 until 288).filter { it * 5 !in 600 until 780 }.flatMap { b -> List(7) { b * 5 } }.toIntArray()
        assertNull(PatternAgp.build(minutes, IntArray(minutes.size) { 120 }, dayCount = 7))
    }

    @Test
    fun aOneBinDipIsSmoothedAway() {
        // 7 days at 120 except a single 5-min reading of 40 each day at 03:00: raw p10 there would be far below 54.
        val minutes = IntArray(288 * 7) { (it % 288) * 5 }
        val values = IntArray(minutes.size) { if (it % 288 == 36) 40 else 120 }
        val agp = PatternAgp.build(minutes, values, dayCount = 7)!!
        assertTrue(agp.p10.all { it >= 54.0 })
        assertTrue("Sporadic, very dangerous hypoglycemia during night" !in detectAgpPatternTexts(agp))
    }
}
