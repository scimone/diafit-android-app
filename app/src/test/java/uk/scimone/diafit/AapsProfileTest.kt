package uk.scimone.diafit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.AapsProfile
import uk.scimone.diafit.core.domain.model.PumpEventEntity
import uk.scimone.diafit.core.domain.model.summary
import uk.scimone.diafit.core.domain.model.toProfileSwitch
import uk.scimone.diafit.core.domain.model.valueAt

class AapsProfileTest {

    private val profileJson = """{"units":"mg/dl","dia":6,
        "sens":[{"time":"00:00","timeAsSeconds":0,"value":84},{"time":"12:00","timeAsSeconds":43200,"value":60}],
        "carbratio":[{"time":"00:00","timeAsSeconds":0,"value":13}],
        "basal":[{"time":"00:00","timeAsSeconds":0,"value":0.5},{"time":"12:00","timeAsSeconds":43200,"value":1.0}],
        "target_low":[{"time":"00:00","timeAsSeconds":0,"value":90}],
        "target_high":[{"time":"00:00","timeAsSeconds":0,"value":90}]}"""

    private fun event(type: String = "Profile Switch", raw: String) = PumpEventEntity(
        id = 1, userId = 1, timestampUtc = 1_000_000L, createdAtUtc = 0L,
        eventType = type, sourceId = "x", rawJson = raw
    )

    @Test fun parsesSchedulesAndTotals() {
        val p = AapsProfile.fromJson(profileJson)!!
        assertEquals(6.0, p.diaHours!!, 0.0)
        assertEquals(2, p.basal.size)
        assertEquals(12 * 0.5 + 12 * 1.0, p.totalDailyBasal, 1e-9)
        assertEquals(84.0, p.isf.valueAt(3600)!!, 0.0)
        assertEquals(60.0, p.isf.valueAt(50000)!!, 0.0)
    }

    @Test fun percentageScalesBasalUpAndRatiosDown() {
        val p = AapsProfile.fromJson(profileJson)!!.atPercentage(80)
        assertEquals(0.4, p.basal.first().value, 1e-9)
        assertEquals(84 / 0.8, p.isf.first().value, 1e-9)
        assertEquals(13 / 0.8, p.carbRatio.first().value, 1e-9)
        assertEquals(90.0, p.targetLow.first().value, 0.0)
    }

    @Test fun profileSwitchEventGetsSummaryAndTemporaryEnd() {
        val raw = """{"percentage":90,"duration":10,"profile":"Anna (90%)","originalProfileName":"Anna","profileJson":${org.json.JSONObject.quote(profileJson)}}"""
        val sw = event(raw = raw).toProfileSwitch()!!
        assertEquals("Anna", sw.baseName)
        assertEquals(90, sw.percentage)
        assertNotNull(sw.profile)
        assertTrue(sw.isActive(1_000_000L + 5 * 60_000L))
        assertEquals(false, sw.isActive(1_000_000L + 11 * 60_000L))
        assertEquals("Anna (90%) · 10 min", event(raw = raw).summary())
    }

    @Test fun otherEventsAreNotProfileSwitches() {
        assertNull(event(type = "Site Change", raw = "{}").toProfileSwitch())
        assertEquals("note", event(type = "Note", raw = "{}").copy(notes = "note").summary())
    }

    @Test fun garbageProfileIsNull() {
        assertNull(AapsProfile.fromJson("not json"))
        assertNull(AapsProfile.fromJson("{}"))
    }
}
