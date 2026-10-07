package uk.scimone.diafit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.ActivityDayStats
import uk.scimone.diafit.core.domain.model.ExerciseEntity
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.SleepStage
import uk.scimone.diafit.core.domain.model.SleepStageEntity
import uk.scimone.diafit.core.domain.model.StepsEntity

class ActivityDayStatsTest {
    private val h = 3_600_000L
    private val dayStart = 100 * 24 * h
    private val dayEnd = dayStart + 24 * h

    @Test fun emptyDataHasNoStats() {
        assertNull(ActivityDayStats.from(ActivityData(), dayStart, dayEnd))
    }

    @Test fun sleepCountsOnTheDayItEndsAndSplitsStages() {
        // Night from 23:00 the evening before to 07:00: 1 h awake, 7 h asleep.
        val s = dayStart - h
        val e = dayStart + 7 * h
        val stages = listOf(
            SleepStageEntity(1, 1, "a", s, s + h, SleepStage.AWAKE.code, s, e),
            SleepStageEntity(2, 1, "a", s + h, e, 4, s, e)
        )
        val stats = ActivityDayStats.from(ActivityData(sleep = stages), dayStart, dayEnd)!!
        assertEquals(7 * h - 0, stats.sleepMs)
        assertEquals(h, stats.awakeMs)
        // The same session does not count for the previous day (it ends after that day).
        assertNull(ActivityDayStats.from(ActivityData(sleep = stages), dayStart - 24 * h, dayStart))
    }

    @Test fun heartRateStepsAndExercise() {
        val hr = (0 until 100).map { HeartRateEntity(userId = 1, timestamp = dayStart + it * 60_000L, bpm = 50 + it) }
        val steps = listOf(StepsEntity(userId = 1, startUtc = dayStart + h, count = 500), StepsEntity(userId = 1, startUtc = dayStart + 2 * h, count = 700))
        val run = ExerciseEntity(userId = 1, sourceId = "r", startUtc = dayStart + 3 * h, endUtc = dayStart + 4 * h, exerciseType = 56, title = "Running")
        val stats = ActivityDayStats.from(ActivityData(hr, steps, exercise = listOf(run)), dayStart, dayEnd)!!
        assertEquals(1200, stats.steps)
        assertEquals(1, stats.exerciseCount)
        assertEquals(h, stats.exerciseMs)
        assertEquals(100, stats.avgBpm) // mean of 50..149
        assertEquals(149, stats.maxBpm)
        assertEquals(52, stats.restingBpm) // mean of the lowest 5 values: 50..54
    }
}
