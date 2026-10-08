package uk.scimone.diafit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
