package uk.scimone.diafit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.history.domain.model.DayGlucoseStats
import uk.scimone.diafit.history.domain.model.GlucoseSample
import uk.scimone.diafit.history.domain.model.GlucoseThresholds
import uk.scimone.diafit.history.domain.model.GlucoseZone

class DayGlucoseStatsTest {
    private val t = GlucoseThresholds(low = 70, high = 180)
    private val min = 60_000L

    private fun series(stepMin: Int, vararg values: Int) =
        values.mapIndexed { i, v -> GlucoseSample(i * stepMin * min, v) }

    @Test
    fun emptyGivesNull() = assertNull(DayGlucoseStats.from(emptyList(), t))

    @Test
    fun zoneSharesAreTimeWeightedNotCountWeighted() {
        // 30 min in range at 5-min steps, then a 30-min sensor gap, then one high reading.
        val samples = series(5, 100, 100, 100, 100, 100, 100) + GlucoseSample(60 * min, 200)
        val stats = DayGlucoseStats.from(samples, t)!!
        // The last in-range reading before the gap counts 5 min, not 30: 30 min in range vs 5 min high.
        assertEquals(30.0 / 35.0, stats.inRangeShare, 1e-9)
        assertEquals(5.0 / 35.0, stats.share(GlucoseZone.HIGH), 1e-9)
    }

    @Test
    fun oneMinuteAndFiveMinuteSourcesAgree() {
        val five = series(5, 100, 100, 200, 200)
        val one = series(1, *IntArray(10) { 100 }, *IntArray(10) { 200 })
        assertEquals(DayGlucoseStats.from(five, t)!!.inRangeShare, DayGlucoseStats.from(one, t)!!.inRangeShare, 1e-9)
    }

    @Test
    fun episodesNeedFifteenMinutesAndSplitByDirection() {
        val samples = series(5, 100, 60, 60, 60, 100, 190, 100, 50, 52, 55, 100)
        val episodes = DayGlucoseStats.from(samples, t)!!.episodes
        // Short high (one reading, 5 min) is dropped; two lows of 15 min each are kept.
        assertEquals(2, episodes.size)
        assertTrue(episodes.all { it.isLow })
        assertEquals(60, episodes[0].extremeMgdl)
        assertEquals(false, episodes[0].isSevere)
        assertEquals(50, episodes[1].extremeMgdl)
        assertEquals(true, episodes[1].isSevere)
    }

    @Test
    fun meanAndCv() {
        val stats = DayGlucoseStats.from(series(5, 100, 200), t)!!
        assertEquals(150.0, stats.meanMgdl, 1e-9)
        assertEquals(50.0 / 150.0 * 100, stats.cvPercent, 1e-9)
    }
}
