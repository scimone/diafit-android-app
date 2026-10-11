package uk.scimone.diafit.patterns

import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.core.domain.model.toSittings
import uk.scimone.diafit.patterns.domain.ActivityEvent
import uk.scimone.diafit.patterns.domain.BINS_PER_DAY
import uk.scimone.diafit.patterns.domain.CombinedDetector
import uk.scimone.diafit.patterns.domain.DayGlucose
import uk.scimone.diafit.patterns.domain.DetectionInput
import uk.scimone.diafit.patterns.domain.SleepNight
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CombinedDetectorTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 14)
    private val now = today.atTime(23, 59).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val thresholds = GlucoseThresholds(low = 70, high = 180)
    private fun date(d: Int) = today.minusDays((27 - d).toLong())
    private fun ms(d: Int, hour: Int, minute: Int = 0) = date(d).atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun days(f: (Int, Int) -> Float) = (0 until 28).map { d -> DayGlucose(date(d), FloatArray(BINS_PER_DAY) { f(d, it) }) }

    private fun meal(d: Int, hour: Int, carbs: Int = 60, type: MealType = MealType.DINNER) = MealEntity(
        userId = 1, description = null, createdAtUtc = ms(d, hour), mealTimeUtc = ms(d, hour), carbohydrates = carbs, proteins = 0, fats = 0,
        impactType = ImpactType.MEDIUM, mealType = type, imageId = "", recommendation = null, reasoning = null, sittingId = "s$d-$hour"
    )

    private fun ids(i: DetectionInput) = CombinedDetector.detect(i).map { it.card.id }

    @Test fun lateDinnersFollowedByHigherNightsAreFound() {
        val late = { d: Int -> d % 2 == 1 }
        val meals = (0 until 27).map { meal(it, if (late(it)) 22 else 19) }
        val d = days { day, bin -> if (bin < 72 && day > 0 && late(day - 1)) 170f else 110f }
        assertTrue("combo.latedinner" in ids(DetectionInput(d, thresholds, now, zone, meals.toSittings())))
    }

    @Test fun noFindingWhenLateDinnersAreNotFollowedByWorseNights() {
        val meals = (0 until 27).map { meal(it, if (it % 2 == 1) 22 else 19) }
        assertTrue(ids(DetectionInput(days { _, _ -> 110f }, thresholds, now, zone, meals.toSittings())).isEmpty())
    }

    @Test fun eveningActivityFollowedByOvernightLowsIsFound() {
        val events = (0 until 27 step 2).map { ActivityEvent(ms(it, 18), ms(it, 19), "Activity", false) }
        val d = days { day, bin -> if (day % 2 == 0 && bin >= 264) 55f else 110f }
        assertTrue("combo.eveninglow" in ids(DetectionInput(d, thresholds, now, zone, events = events)))
    }

    @Test fun shortNightsFollowedByBiggerBreakfastRisesAreFound() {
        val short = { d: Int -> d % 2 == 0 }
        val nights = (0 until 27).map { SleepNight(date(it), ms(it, 7) - (if (short(it)) 330 else 480) * 60_000L, ms(it, 7), if (short(it)) 330 else 480, null, 120) }
        val meals = (0 until 27).map { meal(it, 8, 30, MealType.BREAKFAST) }
        val d = days { day, bin -> if (bin in 96..120) (if (short(day)) 190f else 130f) else 110f }
        assertTrue("combo.shortbreakfast" in ids(DetectionInput(d, thresholds, now, zone, meals.toSittings(), nights = nights)))
    }

    @Test fun nothingWithoutTheData() {
        assertTrue(ids(DetectionInput(days { _, _ -> 110f }, thresholds, now, zone)).isEmpty())
    }
}
