package uk.scimone.diafit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource.AapsTempBasalParser

class AapsTempBasalParserTest {
    private val enacted = """{"algorithm":"SMB","bg":147,"duration":30,"rate":0.3,"reason":"x"}"""

    @Test
    fun parsesEnactedRateAndDuration() {
        val t = AapsTempBasalParser.parse(enacted, 1791456096072, 0.3)!!
        assertEquals(0.3, t.rate, 1e-9)
        assertEquals(30.0, t.durationMinutes, 1e-9)
        assertEquals("temp-basal-1791456096072", AapsTempBasalParser.toEvent(t).sourceId)
    }

    @Test
    fun ignoresMissingOrIncomplete() {
        assertNull(AapsTempBasalParser.parse(null, 1L, null))
        assertNull(AapsTempBasalParser.parse(enacted, 0L, null))
        assertNull(AapsTempBasalParser.parse("""{"units":0.2}""", 1L, null))
        assertNull(AapsTempBasalParser.parse("not json", 1L, null))
    }
}
