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
    fun smbsAreSummedPerHourAndManualBolusesStaySeparate() {
        val entries = listOf(
            bolus(1, 10, 5, 0.2f, true), bolus(2, 10, 20, 0.3f, true), bolus(3, 11, 0, 0.1f, true),
            bolus(4, 10, 30, 1.5f, false)
        ).toBolusEntries(ZoneOffset.UTC)

        assertEquals(3, entries.size)
        val hour10 = entries.single { it.isSmb && it.count == 2 }
        assertEquals(0.5, hour10.units, 1e-6)
        assertEquals(1, entries.single { !it.isSmb }.count)
        assertTrue(entries.map { it.id }.toSet().size == 3)
    }
}
