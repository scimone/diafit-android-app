package uk.scimone.diafit.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.scimone.diafit.core.data.service.syncNotificationTitle
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.patterns.domain.GlucoseSituation
import uk.scimone.diafit.patterns.domain.SituationInput
import uk.scimone.diafit.patterns.domain.msg
import uk.scimone.diafit.patterns.domain.ref
import java.time.ZoneId
import java.time.ZonedDateTime

class GlucoseSituationTest {
    private fun sit(key: String, vararg args: Any) = msg("sit_$key", *args)
    private val zone = ZoneId.of("UTC")
    private val min = 60_000L
    private fun at(h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, 9, h, m, 0, 0, zone).toInstant().toEpochMilli()

    /** One reading per minute for [minutes] up to [now], value from [f] (minutes before now). */
    private fun series(now: Long, minutes: Int, f: (Int) -> Int) =
        (minutes downTo 0).map { CgmEntity(userId = 1, timestamp = now - it * min, valueMgdl = f(it), fiveMinuteRateMgdl = 0f) }

    private fun meal(t: Long, carbs: Int = 60, fat: Int = 10) = MealEntity(
        userId = 1, description = null, createdAtUtc = t, mealTimeUtc = t, carbohydrates = carbs, fats = fat,
        mealType = uk.scimone.diafit.core.domain.model.MealType.LUNCH, imageId = "", recommendation = null, reasoning = null
    )

    private fun bolus(t: Long, u: Float = 4f) = BolusEntity(
        userId = 1, timestampUtc = t, createdAtUtc = t, updatedAtUtc = t, value = u, eventType = "Meal Bolus",
        isSmb = false, pumpType = "", pumpSerial = "", pumpId = 0
    )

    private fun classify(now: Long, readings: List<CgmEntity>, meals: List<MealEntity> = emptyList(), boluses: List<BolusEntity> = emptyList()) =
        GlucoseSituation.classify(SituationInput(now, zone, readings, boluses, meals))

    @Test fun titleShowsDelta() {
        val a = CgmEntity(userId = 1, timestamp = 0, valueMgdl = 120, fiveMinuteRateMgdl = 0f)
        val b = CgmEntity(userId = 1, timestamp = 60_000, valueMgdl = 123, fiveMinuteRateMgdl = 0f)
        assertEquals("123 △+3 mg/dL", syncNotificationTitle(b, a))
        assertEquals("120 △-3 mg/dL", syncNotificationTitle(a.copy(timestamp = 120_000), b))
        assertEquals("123 mg/dL", syncNotificationTitle(b, null))
    }

    @Test fun staleReadingGivesNothing() {
        val now = at(14)
        assertNull(classify(now, series(now - 30 * min, 60) { 100 }))
    }

    @Test fun rapidRiseAfterMeal() {
        val now = at(13)
        val r = series(now, 60) { 100 + (60 - it) * 3 }
        assertEquals(sit("rapid_rise_meal", ref("sit_after_lunch")), classify(now, r, listOf(meal(now - 40 * min)), listOf(bolus(now - 40 * min))))
    }

    @Test fun missedBolus() {
        val now = at(13)
        val r = series(now, 60) { 100 + (60 - it) * 1 }
        assertEquals(sit("missed_bolus"), classify(now, r, listOf(meal(now - 50 * min))))
    }

    @Test fun gentleRiseRightAfterMeal() {
        val now = at(13)
        val r = series(now, 30) { 110 + (30 - it) / 2 }
        assertEquals(sit("rise_meal", ref("sit_after_lunch")), classify(now, r, listOf(meal(now - 10 * min)), listOf(bolus(now - 10 * min))))
    }

    @Test fun steadyButHighSaysHigh() {
        val now = at(15)
        val input = SituationInput(now, zone, series(now, 30) { 190 + (it % 2) }, emptyList(), listOf(meal(now - 100 * min)))
        assertNull(GlucoseSituation.classify(input))
        assertEquals(msg("sit_level_trend", ref("sit_level_high"), ref("sit_trend_steady")), GlucoseSituation.trendFallback(input))
    }

    @Test fun fastingHypo() {
        val now = at(14)
        val r = series(now, 40) { 62 + it / 10 }
        assertEquals(sit("fasting_hypo"), classify(now, r))
    }

    @Test fun nocturnalHypo() {
        val now = at(3)
        assertEquals(sit("nocturnal_hypo"), classify(now, series(now, 40) { 60 + it / 10 }))
    }

    @Test fun postMealHypo() {
        val now = at(14)
        assertEquals(sit("hypo_meal", ref("sit_after_lunch")), classify(now, series(now, 40) { 60 + it / 10 }, listOf(meal(now - 120 * min))))
    }

    @Test fun reboundAfterLow() {
        val now = at(14)
        val r = series(now, 150) { m -> if (m > 90) 55 else 190 }
        assertEquals(sit("rebound_hyper"), classify(now, r))
    }

    @Test fun stableBetweenMeals() {
        val now = at(15)
        assertEquals(sit("stable_between"), classify(now, series(now, 200) { 110 + (it % 3) }))
    }

    @Test fun stableInTheMorning() {
        val now = at(9)
        val r = series(now, 200) { 110 + (it % 3) }
        assertEquals(sit("stable_part", ref("sit_in_morning")), classify(now, r, listOf(meal(now - 120 * min, 15))))
    }

    @Test fun stableOvernight() {
        val now = at(2)
        assertEquals(sit("night_stable"), classify(now, series(now, 200) { 110 + (it % 3) }))
    }

    @Test fun gradualOvernightRise() {
        val now = at(2)
        assertEquals(sit("night_rise"), classify(now, series(now, 200) { 140 - it / 3 }))
    }

    @Test fun persistentHyper() {
        val now = at(15)
        assertEquals(sit("persistent_hyper"), classify(now, series(now, 400) { 230 + (it % 3) }))
    }

    @Test fun prolongedAboveTarget() {
        val now = at(15)
        assertEquals(sit("prolonged_high"), classify(now, series(now, 150) { 230 + (it % 3) }))
    }

    @Test fun correctionDecline() {
        val now = at(15)
        val r = series(now, 100) { 200 - (100 - it) * 1 }
        assertEquals(sit("decline_correction"), classify(now, r, boluses = listOf(bolus(now - 60 * min, 2f))))
    }
}
