package uk.scimone.diafit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.scimone.diafit.core.domain.model.DeviceKind
import uk.scimone.diafit.core.domain.model.NightscoutDeviceStatusParser
import uk.scimone.diafit.core.domain.model.PumpEventEntity
import uk.scimone.diafit.core.domain.model.deviceAges

class DeviceStatusTest {
    private fun doc(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun mergesNewestFieldsAcrossDocuments() {
        val s = NightscoutDeviceStatusParser.parse(listOf(
            doc("""{"created_at":"2026-10-08T10:00:00Z","uploader":{"battery":55}}"""),
            doc("""{"created_at":"2026-10-08T09:55:00Z","pump":{"reservoir":112.5,"battery":{"percent":80}}}""")
        ))!!
        assertEquals(112.5, s.reservoirUnits!!, 0.0)
        assertEquals(80.0, s.pumpBatteryPercent!!, 0.0)
        assertEquals(55.0, s.uploaderBatteryPercent!!, 0.0)
    }

    @Test fun agesUseNewestMatchingChange() {
        fun ev(type: String, t: Long) = PumpEventEntity(userId = 1, timestampUtc = t, createdAtUtc = t, eventType = type, sourceId = "$type$t", rawJson = "{}")
        val now = 100L * 3_600_000
        val ages = deviceAges(listOf(ev("Site Change", 10L * 3_600_000), ev("Site Change", 40L * 3_600_000)), now)
        val site = ages.first { it.kind == DeviceKind.SITE }
        assertEquals(40L * 3_600_000, site.changedAtUtc)
        assertEquals((40 + 72 - 100) * 3_600_000L, site.remainingMs)
        assertNull(ages.first { it.kind == DeviceKind.SENSOR }.changedAtUtc)
    }
}
