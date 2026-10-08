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

    @Test fun patchPumpMergesSiteAndInsulin() {
        fun ev(type: String, t: Long) = PumpEventEntity(userId = 1, timestampUtc = t, createdAtUtc = t, eventType = type, sourceId = "$type$t", rawJson = "{}")
        val h = 3_600_000L
        val together = deviceAges(listOf(ev("Site Change", 10 * h), ev("Insulin Change", 10 * h + 30_000)), 50 * h)
        assertEquals(listOf(DeviceKind.SENSOR, DeviceKind.PATCH), together.map { it.kind })
        val apart = deviceAges(listOf(ev("Site Change", 10 * h), ev("Insulin Change", 30 * h)), 50 * h)
        assertEquals(listOf(DeviceKind.SENSOR, DeviceKind.SITE, DeviceKind.INSULIN), apart.map { it.kind })
    }

    @Test fun batteryHiddenWithoutStatusOrChange() {
        assertEquals(false, deviceAges(emptyList(), 0L).any { it.kind == DeviceKind.BATTERY })
        assertEquals(true, deviceAges(emptyList(), 0L, batteryReported = true).any { it.kind == DeviceKind.BATTERY })
    }

    @Test fun aapsStatusLevels() {
        val s = uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource.AapsDeviceStatusParser
            .parse(mapOf("pumpReservoir" to 112.5, "pumpBattery" to "85%", "glucoseMgdl" to 100.0, "phoneBattery" to 14), 5L)!!
        assertEquals(14.0, s.uploaderBatteryPercent!!, 0.0)
        assertEquals(112.5, s.reservoirUnits!!, 0.0)
        assertEquals(85.0, s.pumpBatteryPercent!!, 0.0)
        assertNull(uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource.AapsDeviceStatusParser.parse(mapOf("iob" to 1.0), 5L))
    }
}
