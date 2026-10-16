package uk.scimone.diafit.dailyrecap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.GlucoseSample
import uk.scimone.diafit.core.domain.model.GlucoseTargetRange
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealSitting
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.dailyrecap.data.DailyRecapScheduler
import uk.scimone.diafit.dailyrecap.data.RecapLlm
import uk.scimone.diafit.dailyrecap.domain.RecapAggregator
import uk.scimone.diafit.dailyrecap.domain.RecapSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DailyRecapTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val date = LocalDate.of(2026, 10, 12)
    private val start = RecapAggregator.dayStart(date, zone)
    private fun at(hour: Int, minute: Int = 0) = start + (hour * 60L + minute) * 60_000L

    private fun reading(t: Long, g: Int) = CgmEntity(userId = 1, timestamp = t, valueMgdl = g, fiveMinuteRateMgdl = 0f)

    /** A day of 5-minute readings: steady 110, a low (52) 03:00-03:30, a high (230) 13:00-14:00. */
    private fun day(): List<CgmEntity> = (0 until 288).map { i ->
        val t = start + i * 5 * 60_000L
        val h = i / 12.0
        reading(t, when {
            h in 3.0..3.5 -> 52
            h in 13.0..14.0 -> 230
            else -> 110
        })
    }

    private fun meal(t: Long, carbs: Int) = MealEntity(
        userId = 1, description = "Pasta", createdAtUtc = t, mealTimeUtc = t, carbohydrates = carbs, impactType = ImpactType.MEDIUM,
        mealType = MealType.LUNCH, imageId = "", recommendation = null, reasoning = null
    )

    private fun bolus(t: Long, u: Float, smb: Boolean = false) = BolusEntity(
        userId = 1, timestampUtc = t, createdAtUtc = t, updatedAtUtc = t, value = u, eventType = "Meal Bolus", isSmb = smb,
        pumpType = "", pumpSerial = "", pumpId = 0
    )

    private fun source(now: Long, previous: List<List<GlucoseSample>> = emptyList()) = RecapSource(
        date = date, zone = zone, nowUtc = now, target = GlucoseTargetRange(70, 180),
        readings = day(), previousDays = previous,
        boluses = listOf(bolus(at(12, 30), 6f), bolus(at(15), 0.4f, smb = true)),
        basalUnits = 18.4,
        sittings = listOf(MealSitting(listOf(meal(at(12, 40), 60)))),
        activity = ActivityData()
    )

    @Test
    fun `summary has the zones, the episodes with their context and the meal outcome`() {
        val (input, visual) = RecapAggregator.build(source(now = at(24) + 1))
        val g = input.glucose!!
        assertTrue(g.veryLowPct in 1..3)
        assertTrue(g.abovePct in 3..6)
        assertEquals("52 at 03:00", g.lowest)
        assertEquals(listOf("low", "high"), input.episodes.map { it.kind })
        // The high started half an hour after lunch: the meal and its insulin are named as context.
        assertTrue(input.episodes[1].before.single().contains("lunch at 12:40, 60 g carbs, 6.0 U"))
        val meal = input.meals.single()
        assertEquals(60, meal.carbsG)
        assertEquals(230, meal.peak)
        assertEquals(6.0, input.insulin!!.mealAndCorrectionBolusU, 0.0)
        assertEquals(0.4, input.insulin!!.autoBolusSmbU, 0.0)
        assertNull(input.partialDayUntil)
        assertEquals(144, visual.points.size)
        assertEquals(5, visual.zoneShares.size)
        assertEquals(1f, visual.zoneShares.sum(), 0.01f)
    }

    @Test
    fun `a day still running only covers the hours so far`() {
        val (input, visual) = RecapAggregator.build(source(now = at(10)))
        assertEquals("10:00", input.partialDayUntil)
        assertTrue(input.meals.isEmpty())
        assertEquals(listOf("night 00-06", "morning 06-12"), input.blocks.map { it.name }.take(2).also { assertTrue(input.blocks.size == 2) })
        assertEquals(at(10), visual.shownUntilUtc)
    }

    @Test
    fun `the summary stays small`() {
        val (input, _) = RecapAggregator.build(source(now = at(24) + 1))
        val json = kotlinx.serialization.json.Json { encodeDefaults = false; explicitNulls = false }
            .encodeToString(uk.scimone.diafit.dailyrecap.domain.RecapInput.serializer(), input)
        assertTrue("summary is ${json.length} chars", json.length < 4_000)
    }

    @Test
    fun `comparison needs three covered days and reports the delta`() {
        val steady = (0 until 288).map { GlucoseSample(start - 86_400_000L + it * 300_000L, 120) }
        assertNull(RecapAggregator.build(source(at(24) + 1, listOf(steady, steady))).first.vsPrevious7Days)
        val cmp = RecapAggregator.build(source(at(24) + 1, listOf(steady, steady, steady))).first.vsPrevious7Days!!
        assertEquals(100, cmp.inRangePctAvg)
        assertTrue(cmp.inRangeDeltaPoints < 0)
    }

    @Test
    fun `an answer with only a headline is rejected`() {
        val empty = """{"headline":"Missed insulin","summary":"x","mood":"mixed","went_well":[],"went_wrong":[],"do_today":[]}"""
        assertTrue(runCatching { RecapLlm.parse(empty) }.isFailure)
    }

    @Test
    fun `llm answer is parsed from fenced json and trimmed to the limits`() {
        val text = RecapLlm.parse(
            """```json
            {"headline":"A steady day","mood":"GOOD","went_well":[{"text":"Calm night","time":""},{"text":"b","time":"12:00"},{"text":"c","time":""}],
             "went_wrong":[],"do_today":["Walk after dinner"," ","x","y","z"]}
            ```"""
        )
        assertEquals("A steady day", text.headline)
        assertEquals("good", text.mood)
        assertEquals(2, text.wentWell.size)
        assertEquals(listOf("Walk after dinner", "x", "y"), text.doToday)
    }

    @Test
    fun `next run is 07-00 today before then and tomorrow after`() {
        val before = DailyRecapScheduler.delayUntilNextRun(zone, LocalDateTime.of(2026, 10, 13, 6, 30))
        assertEquals(30 * 60_000L, before)
        val after = DailyRecapScheduler.delayUntilNextRun(zone, LocalDateTime.of(2026, 10, 13, 7, 0))
        assertEquals(24 * 3_600_000L, after)
    }
}
