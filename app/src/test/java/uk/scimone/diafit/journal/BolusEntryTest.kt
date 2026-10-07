package uk.scimone.diafit.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.journal.presentation.model.toBolusEntries
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BolusEntryTest {
    private fun bolus(id: Int, hour: Int, minute: Int, units: Float, smb: Boolean) = BolusEntity(
        id = id, userId = 1,
        timestampUtc = ZonedDateTime.of(2026, 10, 5, hour, minute, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli(),
        createdAtUtc = 0, updatedAtUtc = 0, value = units, eventType = "", isSmb = smb, pumpType = "", pumpSerial = "", pumpId = 0
    )

    @Test
    fun closeBolusesAreGroupedAndFarOnesStaySeparate() {
        val entries = listOf(
            bolus(1, 10, 5, 0.2f, true), bolus(2, 10, 20, 0.3f, true), bolus(4, 10, 40, 1.5f, false),
            bolus(3, 13, 0, 0.1f, true)
        ).toBolusEntries()

        assertEquals(2, entries.size)
        val group = entries.single { it.count == 3 }
        assertEquals(2.0, group.units, 1e-6)
        assertEquals(3, group.parts.size)
        assertTrue(!group.isSmb)
        assertEquals(1, entries.single { it.count == 1 }.parts.size)
        assertTrue(entries.map { it.id }.toSet().size == 2)
    }
}
