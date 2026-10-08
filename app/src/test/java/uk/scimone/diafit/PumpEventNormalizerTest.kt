package uk.scimone.diafit

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.PumpEventEntity
import uk.scimone.diafit.core.domain.model.PumpEventNormalizer as N
import uk.scimone.diafit.core.domain.model.toProfileSwitch

class PumpEventNormalizerTest {
    private fun event(type: String, ts: Long, raw: String, notes: String? = null) =
        PumpEventEntity(userId = 1, timestampUtc = ts, createdAtUtc = ts, eventType = type, notes = notes, sourceId = "$type$ts", rawJson = raw)

    private val note = event(
        "Note", 1_000_000L,
        """{"eventType":"Note","notes":"Anna (95%)","originalProfileName":"Anna","profileJson":"{\"units\":\"mg/dl\",\"basal\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":0.95}],\"sens\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":88.4210526}],\"carbratio\":[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":10.5263158}]}"}""",
        notes = "Anna (95%)"
    )

    @Test fun profileNoteBecomesSwitchWithSavedProfile() {
        val sw = N.profileNoteToSwitch(note)!!.toProfileSwitch()!!
        assertEquals("Anna (95%)", sw.label)
        assertEquals("Anna", sw.baseName)
        assertEquals(95, sw.percentage)
        assertEquals(1.0, sw.profile!!.basal.single().value, 1e-6)
        assertEquals(84.0, sw.profile!!.isf.single().value, 1e-3)
        assertEquals(10.0, sw.profile!!.carbRatio.single().value, 1e-3)
    }

    @Test fun otherNotesAreLeftAlone() {
        assertNull(N.profileNoteToSwitch(event("Note", 1L, """{"eventType":"Note","notes":"lunch"}""", "lunch")))
        assertNull(N.profileNoteToSwitch(event("Site Change", 1L, """{"eventType":"Site Change"}""")))
    }

    @Test fun duplicatesAndSources() {
        val sw = N.profileNoteToSwitch(note)!!
        val real = event("Profile Switch", 1_000_000L - 10_000, """{"eventType":"Profile Switch","profile":"Anna (95%)","percentage":95}""")
        assertTrue(N.isSameEvent(sw, real))
        assertFalse(N.isSameEvent(sw, real.copy(timestampUtc = 1_000_000L - 600_000)))
        assertFalse(N.isSameEvent(sw, real.copy(rawJson = """{"eventType":"Profile Switch","profile":"Anna","percentage":100}""")))
        assertEquals("Nightscout", N.sourceOf(N.withSource(real, "Nightscout")))
        assertNull(N.sourceOf(real))
        assertTrue(JSONObject(N.withSource(real, "AAPS").rawJson).has(N.SOURCE_KEY))
    }
}
