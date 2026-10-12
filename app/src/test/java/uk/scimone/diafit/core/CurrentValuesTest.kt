package uk.scimone.diafit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.ActivityLevel
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.currentActivityLevel
import uk.scimone.diafit.core.domain.model.remainingCarbs
import uk.scimone.diafit.core.domain.model.remainingInsulin

class CurrentValuesTest {
    private val min = 60_000L

    @Test
    fun insulinIsFullAtInjectionAndGoneAfterDia() {
        assertEquals(2.0, remainingInsulin(listOf(0L to 2.0), 0L), 1e-6)
        assertEquals(0.0, remainingInsulin(listOf(0L to 2.0), (4.5 * 60 * min).toLong()), 1e-9)
        val half = remainingInsulin(listOf(0L to 2.0), 90 * min)
        assertTrue(half in 0.3..1.7)
    }

    @Test
    fun carbsOnBoardIsTheSumOfRemainingCarbsAndDeclines() {
        val engine = uk.scimone.diafit.mealactivity.domain.MealActivityEngine()
        fun curve(g: Double) = engine.curve(uk.scimone.diafit.mealactivity.domain.MealInput(g, 0.0, 0.0, null, uk.scimone.diafit.mealactivity.domain.AbsorptionSpeed.MEDIUM, 0L))!!
        val meals = listOf(curve(40.0), curve(20.0))
        assertEquals(60.0, remainingCarbs(meals, 0L), 1e-6)
        assertEquals(60.0, remainingCarbs(meals, -10 * min), 1e-6) // not yet eaten: all still to come
        assertTrue(remainingCarbs(meals, 120 * min) < 60.0)
        assertEquals(0.0, remainingCarbs(meals, 24 * 60 * min), 0.05)
    }

    @Test
    fun activityLevelFollowsHeartRateAboveResting() {
        val base = (0 until 100).map { HeartRateEntity(userId = 1, timestamp = it * min, bpm = 60) }
        assertEquals(ActivityLevel.LOW, currentActivityLevel(ActivityData(heartRate = base), 99 * min))
        val moving = base + HeartRateEntity(userId = 1, timestamp = 100 * min, bpm = 100)
        assertEquals(ActivityLevel.MODERATE, currentActivityLevel(ActivityData(heartRate = moving), 100 * min))
        assertEquals(null, currentActivityLevel(ActivityData(), 0L))
    }
}
