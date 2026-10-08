package uk.scimone.diafit.history

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.scimone.diafit.history.domain.model.TreatmentEvent
import uk.scimone.diafit.history.presentation.components.groupForBand

class TreatmentBandTest {
    private val min = 60_000L

    @Test fun entriesFarApartStaySeparateBlocks() {
        val blocks = listOf(TreatmentEvent(0, 40f), TreatmentEvent(60 * min, 20f)).groupForBand(20 * min)
        assertEquals(2, blocks.size)
        assertEquals(40f, blocks[0].total, 0.001f)
    }

    @Test fun manyEntriesInAShortTimeBecomeOneBlock() {
        val events = listOf(0L, 8L, 15L, 30L).map { TreatmentEvent(it * min, 1f) }
        val blocks = events.groupForBand(20 * min)
        assertEquals(1, blocks.size)
        assertEquals(4f, blocks[0].total, 0.001f)
        assertEquals(30 * min, blocks[0].endUtc)
    }

    @Test fun zeroGapOnlyJoinsIdenticalTimes() {
        assertEquals(2, listOf(TreatmentEvent(0, 1f), TreatmentEvent(min, 1f)).groupForBand(0L).size)
    }
}
