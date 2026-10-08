package uk.scimone.diafit.backendsync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import uk.scimone.diafit.backendsync.data.BackendApi
import uk.scimone.diafit.backendsync.domain.CgmIn
import uk.scimone.diafit.backendsync.domain.MealIn
import uk.scimone.diafit.backendsync.domain.isoUtc
import uk.scimone.diafit.backendsync.domain.toBackend
import uk.scimone.diafit.backendsync.domain.toBackendSession
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.SleepStageEntity
import uk.scimone.diafit.core.domain.model.StepsEntity
import uk.scimone.diafit.backendsync.domain.StepsIn

class BackendPayloadsTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `timestamps are UTC with an offset`() {
        assertEquals("2026-10-08T12:00:00Z", isoUtc(1_791_460_800_000))
    }

    @Test
    fun `cgm uses the API field names and maps unknown directions to NONE`() {
        val payload = CgmEntity(userId = 1, timestamp = 0, valueMgdl = 120, fiveMinuteRateMgdl = 1.5f, direction = "weird")
            .toBackend()!!
        assertEquals("NONE", payload.direction)
        val obj = json.encodeToJsonElement(CgmIn.serializer(), payload).jsonObject
        assertEquals(setOf("timestamp", "value_mgdl", "five_minute_rate_mgdl", "direction", "device", "source", "source_id"), obj.keys)
    }

    @Test
    fun `out of range cgm values are dropped`() {
        assertNull(CgmEntity(userId = 1, timestamp = 0, valueMgdl = 0, fiveMinuteRateMgdl = 0f).toBackend())
    }

    @Test
    fun `merged and deleted meals are invalid, app meals get a stable source id`() {
        val meal = MealEntity(id = 7, userId = 1, description = "Pasta", createdAtUtc = 1000, mealTimeUtc = 2000,
            imageId = "", recommendation = null, reasoning = null, mergedIntoId = 3)
        val payload = meal.toBackend()
        assertFalse(payload.isValid)
        assertNull(payload.imageId)
        assertEquals("diafit-meal-1000-7", payload.sourceId)
        assertEquals("Diafit", payload.source)
        val obj = json.encodeToJsonElement(MealIn.serializer(), payload).jsonObject
        assertEquals("SNACK", obj["meal_type"].toString().trim('"'))
    }

    @Test
    fun `sleep session from its stage rows`() {
        val rows = listOf(
            SleepStageEntity(userId = 1, sessionId = "s", startUtc = 60_000, endUtc = 120_000, stage = 5, sessionStartUtc = 0, sessionEndUtc = 120_000),
            SleepStageEntity(userId = 1, sessionId = "s", startUtc = 0, endUtc = 60_000, stage = 4, sessionStartUtc = 0, sessionEndUtc = 120_000)
        )
        val session = rows.toBackendSession()!!
        assertEquals("s", session.sourceId)
        assertEquals(listOf(4, 5), session.stages.map { it.stage })
    }

    @Test
    fun `step slot spans 15 minutes with a stable source id`() {
        val payload = StepsEntity(userId = 1, startUtc = 1_791_460_800_000, count = 420).toBackend()!!
        assertEquals("2026-10-08T12:00:00Z", payload.startTime)
        assertEquals("2026-10-08T12:15:00Z", payload.endTime)
        assertEquals("hc-steps-1791460800000", payload.sourceId)
        val obj = json.encodeToJsonElement(StepsIn.serializer(), payload).jsonObject
        assertEquals(setOf("start_time", "end_time", "count", "device", "source", "source_id"), obj.keys)
    }

    @Test
    fun `api root accepts host or api path`() {
        assertEquals("https://x.org/api/v2", BackendApi.apiRoot("https://x.org/"))
        assertEquals("https://x.org/api/v2", BackendApi.apiRoot(" https://x.org/api/v2/ "))
    }
}
