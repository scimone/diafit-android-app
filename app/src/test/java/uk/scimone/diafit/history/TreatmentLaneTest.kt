package uk.scimone.diafit.history

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.scimone.diafit.history.presentation.components.mergeNearby

class TreatmentLaneTest {
    @Test fun marksFarApartStaySeparate() {
        val merged = listOf(10f to 40f, 100f to 60f).mergeNearby(8f)
        assertEquals(listOf(10f, 100f), merged.map { it.x })
        assertEquals(listOf(40f, 60f), merged.map { it.total })
    }

    @Test fun closeMarksAreSummedAtTheirWeightedCentre() {
        val merged = listOf(20f to 10f, 14f to 30f, 100f to 5f).mergeNearby(8f)
        assertEquals(2, merged.size)
        assertEquals(40f, merged[0].total, 0.001f)
        assertEquals(15.5f, merged[0].x, 0.001f)
    }

    @Test fun zeroGapNeverMerges() {
        assertEquals(2, listOf(5f to 1f, 5f to 1f).mergeNearby(0f).size)
    }
}
