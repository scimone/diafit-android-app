package uk.scimone.diafit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.AgpProfile

class AgpProfileTest {
    @Test
    fun emptyGivesNull() = assertNull(AgpProfile.builder().build())

    @Test
    fun percentilesOfOneBin() {
        val b = AgpProfile.builder()
        // 100 readings 101..200 at 08:00 -> bin 32; every other bin is empty.
        for (v in 101..200) b.add(8 * 60, v)
        val p = b.build()!!
        assertEquals(100, p.readingCount)
        assertEquals(105f, p.p5[32], 0.01f)
        assertEquals(150f, p.median[32], 0.01f)
        assertEquals(195f, p.p95[32], 0.01f)
        assertTrue(p.median[0].isNaN())
    }

    @Test
    fun sparseBinsAreGaps() {
        val b = AgpProfile.builder()
        repeat(4) { b.add(0, 100) }
        assertTrue(b.build()!!.median[0].isNaN())
    }
}
