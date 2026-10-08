package uk.scimone.diafit.core

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.scimone.diafit.core.domain.model.BasalSegment
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.SmbMark
import uk.scimone.diafit.core.domain.model.basalInsulinActivity
import uk.scimone.diafit.core.domain.model.deliveredUnits
import uk.scimone.diafit.core.domain.model.toSmbMarks

class SmbMarkTest {
    private fun bolus(minute: Int, units: Float, smb: Boolean = true) = BolusEntity(
        userId = 1, timestampUtc = minute * 60_000L, createdAtUtc = 0, updatedAtUtc = 0, value = units,
        eventType = "SMB", isSmb = smb, pumpType = "", pumpSerial = "", pumpId = 0
    )

    @Test fun nearbySmbsBecomeOneMark() {
        val marks = listOf(bolus(0, 0.2f), bolus(5, 0.3f), bolus(10, 0.5f), bolus(60, 0.4f)).toSmbMarks()
        assertEquals(2, marks.size)
        assertEquals(1.0, marks[0].units, 1e-6)
        assertEquals(3, marks[0].count)
        assertEquals(0.4, marks[1].units, 1e-6)
    }

    @Test fun aGroupNeverSpansMoreThanHalfAnHour() {
        // A steady SMB every 10 minutes must still split into several triangles.
        val marks = (0..6).map { bolus(it * 10, 0.1f) }.toSmbMarks()
        assertEquals(2, marks.size)
    }

    @Test fun manualBolusesAreIgnored() {
        assertEquals(0, listOf(bolus(0, 3f, smb = false)).toSmbMarks().size)
    }

    @Test fun smbAddsToBasalActivityAndBasalUnitsAreIntegrated() {
        val seg = listOf(BasalSegment(0, 2 * 3_600_000L, delivered = 1.0, scheduled = 1.0))
        assertEquals(2.0, seg.deliveredUnits(0, 3_600_000L * 4), 1e-9)
        assertEquals(1.0, seg.deliveredUnits(0, 3_600_000L), 1e-9)
        val without = basalInsulinActivity(seg, 0, 2 * 3_600_000L)
        val with = basalInsulinActivity(seg, 0, 2 * 3_600_000L, smbs = listOf(SmbMark(0, 1.0)))
        assertEquals(0.0, without.maxOf { it.second }, 1e-12)
        assert(with.maxOf { it.second } > 0.0)
    }
}
