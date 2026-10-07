package uk.scimone.diafit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.ElevatedActivity
import uk.scimone.diafit.core.domain.model.HeartRateBucket
import uk.scimone.diafit.core.domain.model.SleepSession
import uk.scimone.diafit.core.domain.model.SleepStageEntity
import uk.scimone.diafit.core.domain.model.StepsEntity

class ElevatedActivityTest {
    private val m = 60_000L
    private val slot = ElevatedActivity.SLOT_MS
    private val t0 = 1_000_000 * slot

    private fun quietDay(slots: Int = 288) = (0 until slots).map { HeartRateBucket(t0 + it * slot, 60.0) }

    @Test fun nothingHappensOnAQuietDay() {
        assertTrue(ElevatedActivity.detect(quietDay(), emptyList(), emptyList()).isEmpty())
    }

    @Test fun sustainedHighHeartRateIsFoundAndBriefSpikesAreNot() {
        val hr = quietDay().map {
            when (it.startUtc) {
                t0 + 20 * slot -> it.copy(bpm = 130.0) // a single spike
                in (t0 + 100 * slot)..(t0 + 109 * slot) -> it.copy(bpm = 140.0) // 50 min
                else -> it
            }
        }
        val spans = ElevatedActivity.detect(hr, emptyList(), emptyList())
        assertEquals(1, spans.size)
        assertEquals(t0 + 100 * slot, spans[0].startUtc)
        assertEquals(t0 + 110 * slot, spans[0].endUtc)
    }

    @Test fun stepsAloneCountAndGapsAreBridged() {
        // 15 min of walking, a 10-minute pause, 15 more minutes: one range.
        val steps = listOf(
            StepsEntity(userId = 1, startUtc = t0 + 3 * slot, count = 900),
            StepsEntity(userId = 1, startUtc = t0 + 8 * slot, count = 900)
        )
        val spans = ElevatedActivity.detect(quietDay(), steps, emptyList())
        assertEquals(1, spans.size)
        assertEquals(t0 + 3 * slot, spans[0].startUtc)
        assertEquals(t0 + 11 * slot, spans[0].endUtc)
    }

    @Test fun lightStepCountsDoNotQualify() {
        val steps = listOf(StepsEntity(userId = 1, startUtc = t0, count = 200))
        assertTrue(ElevatedActivity.detect(quietDay(), steps, emptyList()).isEmpty())
    }

    @Test fun sleepSlotsNeverCount() {
        val hr = quietDay().map { if (it.startUtc in (t0 + 10 * slot)..(t0 + 20 * slot)) it.copy(bpm = 120.0) else it }
        val night = SleepSession(listOf(SleepStageEntity(1, 1, "s", t0, t0 + 30 * slot, 4, t0, t0 + 30 * slot)))
        assertTrue(ElevatedActivity.detect(hr, emptyList(), listOf(night)).isEmpty())
        assertEquals(60, ElevatedActivity.restingBpm(hr))
    }
}
