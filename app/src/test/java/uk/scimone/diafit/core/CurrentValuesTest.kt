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
    fun carbsDeclineAcrossTheAbsorptionWindow() {
        assertEquals(40.0, remainingCarbs(listOf(Triple(0L, 40.0, 240)), 0L), 1e-6)
        assertEquals(0.0, remainingCarbs(listOf(Triple(0L, 40.0, 240)), 240 * min), 1e-9)
        assertTrue(remainingCarbs(listOf(Triple(0L, 40.0, 240)), 120 * min) < 40.0)
    }

    @Test
    fun activityLevelFollowsHeartRateAboveResting() {
        val base = (0 until 100).map { HeartRateEntity(userId = 1, timestamp = it * min, bpm = 60) }
        assertEquals(ActivityLevel.RESTING, currentActivityLevel(ActivityData(heartRate = base), 99 * min))
        val moving = base + HeartRateEntity(userId = 1, timestamp = 100 * min, bpm = 100)
        assertEquals(ActivityLevel.MODERATE, currentActivityLevel(ActivityData(heartRate = moving), 100 * min))
        assertEquals(null, currentActivityLevel(ActivityData(), 0L))
    }
}
