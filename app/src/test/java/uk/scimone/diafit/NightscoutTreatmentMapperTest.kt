package uk.scimone.diafit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.dataType
import uk.scimone.diafit.core.domain.model.toProfileSwitch
import uk.scimone.diafit.core.data.networking.dto.NightscoutTreatmentMapper as M

class NightscoutTreatmentMapperTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun mealBolusGivesBolusAndMeal() {
        val t = obj("""{"_id":"a1","eventType":"Meal Bolus","date":1700000000000,"insulin":4.5,"carbs":40}""")
        val bolus = M.toBolus(t)!!
        assertEquals(4.5f, bolus.value)
        assertEquals("a1", bolus.sourceId)
        assertEquals(40, M.toMeal(t)!!.carbohydrates)
        assertNull(M.toTempBasal(t))
    }

    @Test fun smbAndCreatedAtFallback() {
        val t = obj("""{"_id":"b","eventType":"SMB","created_at":"2026-10-01T10:00:00.000Z","insulin":0.3,"isSMB":true}""")
        val bolus = M.toBolus(t)!!
        assertTrue(bolus.isSmb)
        assertEquals(1790848800000L, bolus.timestampUtc)
        assertNull(M.toMeal(t))
    }

    @Test fun tempBasalStartAndCancel() {
        val start = M.toTempBasal(obj("""{"_id":"c","eventType":"Temp Basal","date":1700000000000,"duration":30,"absolute":0.85}"""))!!
        assertEquals(0.85, start.rate!!, 1e-9)
        assertEquals(30.0, start.durationMinutes!!, 1e-9)
        val cancel = M.toTempBasal(obj("""{"_id":"d","eventType":"Temp Basal","date":1700000600000,"duration":0}"""))!!
        assertEquals(0.0, cancel.rate!!, 1e-9)
        // A running temp with only a percentage has no usable absolute rate.
        assertNull(M.toTempBasal(obj("""{"eventType":"Temp Basal","date":1700000000000,"duration":30,"percent":-50}""")))
    }

    @Test fun siteChangeAndProfileSwitchBecomeEventsOfTheRightType() {
        val site = M.toPumpEvent(obj("""{"_id":"s1","eventType":"Site Change","created_at":"2026-10-01T10:00:00.000Z","notes":"pod"}"""))!!
        assertEquals("s1", site.sourceId)
        assertEquals(uk.scimone.diafit.settings.domain.model.DataType.DEVICE, site.dataType)
        val sw = M.toPumpEvent(obj("""{"_id":"p1","eventType":"Profile Switch","date":1700000000000,"duration":60,"percentage":90,"profile":"Anna"}"""))!!
        assertEquals(uk.scimone.diafit.settings.domain.model.DataType.PROFILE, sw.dataType)
        assertEquals(60.0, sw.durationMinutes!!, 1e-9)
        // Boluses, carbs and temp basals have their own paths.
        assertNull(M.toPumpEvent(obj("""{"eventType":"Meal Bolus","date":1700000000000,"insulin":2}""")))
        assertNull(M.toPumpEvent(obj("""{"eventType":"Temp Basal","date":1700000000000,"duration":30,"absolute":1.0}""")))
        assertNull(M.toPumpEvent(obj("""{"eventType":"Site Change","date":1700000000000,"isValid":false}""")))
    }

    @Test fun profileDocBecomesProfileSwitchWithProfileJson() {
        val doc = obj("""{"_id":"d1","defaultProfile":"Anna","startDate":"2026-09-01T00:00:00.000Z","store":{"Anna":{"dia":5,"units":"mg/dl","basal":[{"time":"00:00","timeAsSeconds":0,"value":0.8}],"sens":[{"time":"00:00","timeAsSeconds":0,"value":84}],"carbratio":[{"time":"00:00","timeAsSeconds":0,"value":10}]}}}""")
        val e = M.profileDocToSwitch(doc)!!
        val sw = e.toProfileSwitch()!!
        assertEquals("Anna", sw.label)
        assertEquals(100, sw.percentage)
        assertEquals(0.8, sw.profile!!.basal.single().value, 1e-9)
        assertNull(M.profileDocToSwitch(obj("""{"startDate":"2026-09-01T00:00:00.000Z"}""")))
    }
}
