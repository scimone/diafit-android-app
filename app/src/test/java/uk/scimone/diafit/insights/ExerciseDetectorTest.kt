package uk.scimone.diafit.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.model.ExerciseEntity
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.core.domain.model.HeartRateEntity
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.core.domain.model.toSittings
import uk.scimone.diafit.insights.domain.ActivityEvent
import uk.scimone.diafit.insights.domain.ActivityEvents
import uk.scimone.diafit.insights.domain.BINS_PER_DAY
import uk.scimone.diafit.insights.domain.DayGlucose
import uk.scimone.diafit.insights.domain.DetectionInput
import uk.scimone.diafit.insights.domain.ExerciseDetector
import uk.scimone.diafit.insights.domain.InsightTone
import uk.scimone.diafit.insights.domain.MiniGraph
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ExerciseDetectorTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 14)
    private val now = today.atTime(23, 59).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val thresholds = GlucoseThresholds(low = 70, high = 180)

    private fun startMs(day: Int, hour: Int) = today.minusDays((27 - day).toLong()).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private class Scenario(val days: List<DayGlucose>, val events: List<ActivityEvent>)

    /**
     * 28 days of glucose; on each day in [activeDays] there is one activity at [hour] lasting [duration] minutes.
     * [profile] gives the glucose (day, minutes since the activity's start, duration) around it; elsewhere 110.
     */
    private fun scenario(
        activeDays: Set<Int> = (0..26).toSet(), hour: (Int) -> Int = { 10 }, duration: (Int) -> Int = { 40 },
        bpm: (Int) -> Double? = { null }, profile: (Int, Int, Int) -> Float
    ): Scenario {
        val days = (0 until 28).map { d ->
            DayGlucose(today.minusDays((27 - d).toLong()), FloatArray(BINS_PER_DAY) { bin ->
                if (d in activeDays) profile(d, bin * 5 - hour(d) * 60, duration(d)) else 110f
            })
        }
        val events = activeDays.sorted().map { d ->
            ActivityEvent(startMs(d, hour(d)), startMs(d, hour(d)) + duration(d) * 60_000L, "Running", true, bpm(d))
        }
        return Scenario(days, events)
    }

    private fun detect(s: Scenario, meals: List<MealEntity> = emptyList(), events: List<ActivityEvent> = s.events) =
        ExerciseDetector.detect(DetectionInput(s.days, thresholds, now, zone, meals.toSittings(), emptyList(), events))

    private fun flat(@Suppress("UNUSED_PARAMETER") d: Int, @Suppress("UNUSED_PARAMETER") m: Int, @Suppress("UNUSED_PARAMETER") dur: Int) = 110f

    @Test fun glucoseDroppingDuringActivityIsFound() {
        val s = scenario { _, m, dur -> if (m in 0..dur) 110f - 50f * m / dur else if (m > dur) 60f + 50f * (m - dur).coerceAtMost(60) / 60f else 110f }
        val drop = detect(s).single { it.card.detector == "ex-drop" }
        assertEquals(InsightTone.UNFAVORABLE, drop.card.tone)
        assertTrue(drop.card.miniGraph is MiniGraph.EventResponse)
        assertEquals("start", (drop.card.miniGraph as MiniGraph.EventResponse).eventLabel)
        assertTrue((drop.card.miniGraph as MiniGraph.EventResponse).spanMinutes!! > 0)
    }

    @Test fun lowsSoonAfterActivityAreFound() {
        val lowDays = setOf(1, 4, 7, 10, 13, 16, 19, 22)
        val s = scenario { d, m, dur -> if (d in lowDays && m in (dur + 20)..(dur + 90)) 58f else 110f }
        val low = detect(s).single { it.card.detector == "ex-low" }
        assertEquals(8, low.card.positiveCount)
        assertEquals(26 + 0, low.card.sampleSize.coerceAtLeast(26).coerceAtMost(26))
    }

    @Test fun delayedLowsAreKeptApartFromImmediateOnes() {
        val lowDays = setOf(1, 4, 7, 10, 13, 16, 19, 22)
        val s = scenario { d, m, dur -> if (d in lowDays && m in (dur + 200)..(dur + 330)) 58f else 110f }
        val found = detect(s)
        assertTrue(found.any { it.card.detector == "ex-delayed" })
        assertTrue(found.none { it.card.detector == "ex-low" })
    }

    @Test fun risesAreMixedNotUnfavourable() {
        val s = scenario { _, m, dur -> if (m in 0..dur + 60) 110f + 60f * (m.coerceAtMost(dur)) / dur else 110f }
        val rise = detect(s).single { it.card.detector == "ex-rise" }
        assertEquals(InsightTone.MIXED, rise.card.tone)
    }

    @Test fun steadyGlucoseIsFavourable() {
        val found = detect(scenario(profile = ::flat))
        assertEquals(InsightTone.FAVORABLE, found.single { it.card.detector == "ex-good" }.card.tone)
        assertTrue(found.none { it.card.tone == InsightTone.UNFAVORABLE })
    }

    @Test fun tooFewActivitiesGiveNothing() {
        assertTrue(detect(scenario(activeDays = setOf(3, 8, 12, 20), profile = ::flat)).none { it.card.detector != "ex-nextday" })
    }

    @Test fun activityWithAMealCloseByIsLeftOut() {
        val s = scenario { _, m, dur -> if (m in 0..dur) 110f - 50f * m / dur else 110f }
        val meals = (0..26).map { d ->
            val t = startMs(d, 10) - 30 * 60_000L
            MealEntity(userId = 1, description = null, createdAtUtc = t, mealTimeUtc = t, carbohydrates = 60, mealType = MealType.BREAKFAST, imageId = "", recommendation = null, reasoning = null, sittingId = "m$d")
        }
        assertTrue(detect(s, meals).isEmpty())
    }

    @Test fun backToBackActivitiesAreLeftOut() {
        val s = scenario { _, m, dur -> if (m in 0..dur) 110f - 50f * m / dur else 110f }
        val doubled = s.events.flatMap { listOf(it, it.copy(startUtc = it.endUtc + 60 * 60_000L, endUtc = it.endUtc + 100 * 60_000L)) }
        assertTrue(detect(s, events = doubled).none { it.card.detector == "ex-drop" })
    }

    @Test fun activityThatStartsLowIsLeftOut() {
        val s = scenario { _, m, dur -> if (m in -30..0) 60f else if (m in 1..dur) 110f - 50f * m / dur else 110f }
        assertTrue(detect(s).isEmpty())
    }

    @Test fun poorSensorCoverageIsLeftOut() {
        val s = scenario { _, m, dur -> if (m in 0..dur) 110f - 50f * m / dur else 110f }
        val gappy = s.days.map { d -> DayGlucose(d.date, FloatArray(BINS_PER_DAY) { if (it % 3 == 0) d.bins[it] else Float.NaN }) }
        assertTrue(detect(Scenario(gappy, s.events)).isEmpty())
    }

    @Test fun longSessionsWithLowerGlucoseAfterwardsAreFound() {
        val long = (0..26).filter { it % 2 == 0 }.toSet()
        val s = scenario(duration = { if (it in long) 60 else 25 }) { d, m, dur -> if (d in long && m in (dur + 40)..(dur + 300)) 72f else 110f }
        val l = detect(s).single { it.card.detector == "ex-long" }
        assertTrue(l.card.effectSize >= 20)
        assertTrue(l.card.limitations.any { "can't explain" in it })
    }

    @Test fun eveningActivityWithLowerGlucoseAfterwardsIsFound() {
        val evening = (0..26).filter { it % 2 == 0 }.toSet()
        val s = scenario(hour = { if (it in evening) 18 else 10 }) { d, m, dur -> if (d in evening && m in (dur + 40)..(dur + 300)) 72f else 110f }
        assertTrue(detect(s).any { it.card.detector == "ex-evening" })
    }

    @Test fun harderActivityWithBiggerDropsIsFound() {
        val hard = (0..26).filter { it % 2 == 0 }.toSet()
        val s = scenario(bpm = { if (it in hard) 150.0 else 100.0 }) { d, m, dur -> if (d in hard && m in 0..dur) 110f - 45f * m / dur else if (d in hard && m > dur) 65f else 110f }
        val i = detect(s).single { it.card.detector == "ex-intensity" }
        assertTrue(i.card.title.contains("drop"))
        assertEquals(InsightTone.MIXED, i.card.tone)
    }

    @Test fun steadierDaysAfterActiveDaysAreFound() {
        val active = (0..26).filter { it % 2 == 0 }.toSet()
        // The day after an active day is flat; the day after a quiet day spends a long stretch high.
        val days = (0 until 28).map { d ->
            DayGlucose(today.minusDays((27 - d).toLong()), FloatArray(BINS_PER_DAY) { bin -> if (d % 2 == 1 || bin !in 100..200) 120f else 240f })
        }
        val events = active.map { ActivityEvent(startMs(it, 10), startMs(it, 10) + 40 * 60_000L, "Running", true) }
        val n = ExerciseDetector.detect(DetectionInput(days, thresholds, now, zone, emptyList(), emptyList(), events)).single { it.card.detector == "ex-nextday" }
        assertEquals(InsightTone.FAVORABLE, n.card.tone)
        assertTrue(n.card.limitations.any { "watch" in it })
    }

    @Test fun eventsAreBuiltFromWorkoutsAndHeartRate() {
        val start = startMs(5, 10)
        val hr = (0 until 40).map { HeartRateEntity(userId = 1, timestamp = start + it * 60_000L, bpm = 150) }
        val data = ActivityData(heartRate = hr, exercise = listOf(ExerciseEntity(userId = 1, sourceId = "a", startUtc = start, endUtc = start + 40 * 60_000L, exerciseType = 56, title = "Running")))
        val events = ActivityEvents.build(data)
        assertEquals(1, events.size)
        assertEquals("Running", events[0].label)
        assertTrue(events[0].meanBpm!! > 140)
    }
}
