package uk.scimone.diafit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.scimone.diafit.core.domain.model.AapsProfile
import uk.scimone.diafit.core.domain.model.ProfileStep
import uk.scimone.diafit.core.domain.model.ProfileSwitch
import uk.scimone.diafit.core.domain.model.PumpEventEntity
import uk.scimone.diafit.core.domain.model.buildBasalTimeline
import java.time.ZoneOffset

class BasalTimelineTest {
    private val zone = ZoneOffset.UTC
    private val hour = 3_600_000L
    private val profile = AapsProfile(
        units = "mg/dl", diaHours = 5.0,
        basal = listOf(ProfileStep(0, 0.3), ProfileStep(6 * 3600, 0.5)),
        isf = emptyList(), carbRatio = emptyList(), targetLow = emptyList(), targetHigh = emptyList()
    )
    private fun switch(start: Long, percentage: Int = 100, duration: Int = 0) =
        ProfileSwitch(1, start, "P", "P", percentage, 0, duration, profile)
    private fun temp(at: Long, rate: Double, minutes: Double) = PumpEventEntity(
        userId = 1, timestampUtc = at, createdAtUtc = at, eventType = "Temp Basal",
        durationMinutes = minutes, rate = rate, sourceId = "t$at", rawJson = "{}"
    )

    @Test
    fun scheduledBasalFollowsTheProfileSteps() {
        val seg = buildBasalTimeline(listOf(switch(0)), emptyList(), 4 * hour, 8 * hour, zone)
        assertEquals(2, seg.size)
        assertEquals(0.3, seg[0].delivered, 1e-9); assertEquals(6 * hour, seg[0].endUtc)
        assertEquals(0.5, seg[1].delivered, 1e-9)
    }

    @Test
    fun percentageScalesBasalAndEndedTemporarySwitchFallsBack() {
        val sw = switch(0, percentage = 200, duration = 60)
        val seg = buildBasalTimeline(listOf(sw), emptyList(), 0, 2 * hour, zone)
        assertEquals(0.6, seg[0].delivered, 1e-9); assertEquals(hour, seg[0].endUtc)
        assertEquals(0.3, seg[1].delivered, 1e-9)
    }

    @Test
    fun tempBasalOverridesForItsDurationAndKeepsScheduledRate() {
        val seg = buildBasalTimeline(listOf(switch(0)), listOf(temp(hour, 0.0, 30.0)), 0, 2 * hour, zone)
        assertEquals(3, seg.size)
        assertEquals(0.0, seg[1].delivered, 1e-9)
        assertEquals(0.3, seg[1].scheduled!!, 1e-9)
        assertEquals(hour + 30 * 60_000, seg[1].endUtc)
        assertEquals(0.3, seg[2].delivered, 1e-9)
    }

    @Test
    fun newerTempReplacesOlderOne() {
        val temps = listOf(temp(0, 1.0, 120.0), temp(hour, 0.2, 30.0))
        val seg = buildBasalTimeline(emptyList(), temps, 0, 2 * hour, zone)
        assertEquals(listOf(1.0, 0.2), seg.map { it.delivered })  // the replaced temp does not resume
        assertNull(seg[0].scheduled)
    }

    @Test
    fun nothingKnownGivesNoSegments() {
        assertEquals(0, buildBasalTimeline(emptyList(), emptyList(), 0, hour, zone).size)
    }
}
