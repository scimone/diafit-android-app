package uk.scimone.diafit.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.core.domain.model.toSittings
import uk.scimone.diafit.patterns.domain.BINS_PER_DAY
import uk.scimone.diafit.patterns.domain.DayGlucose
import uk.scimone.diafit.patterns.domain.DetectionInput
import uk.scimone.diafit.patterns.domain.PatternTone
import uk.scimone.diafit.patterns.domain.MealDetector
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.sin

class MealDetectorTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 14)
    private val now = today.atTime(23, 59).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val thresholds = GlucoseThresholds(low = 70, high = 180)
    private val dinnerBin = 19 * 12

    /** 28 days, oldest first; glucose is 110 except 0..300 minutes after dinner, where [profile] (day index, minutes) decides. */
    private fun days(coverage: (Int, Int) -> Boolean = { _, _ -> true }, profile: (Int, Int) -> Float): List<DayGlucose> =
        (0 until 28).map { i ->
            DayGlucose(today.minusDays((27 - i).toLong()), FloatArray(BINS_PER_DAY) { bin ->
                val m = (bin - dinnerBin) * 5
                when {
                    !coverage(i, bin) -> Float.NaN
                    m in 0..300 -> profile(i, m)
                    else -> 110f
                }
            })
        }

    private fun meal(day: Int, hour: Int = 19, minute: Int = 0, carbs: Int = 60, protein: Int = 0, fat: Int = 0, impact: ImpactType = ImpactType.MEDIUM, type: MealType = MealType.DINNER): MealEntity {
        val t = today.minusDays((27 - day).toLong()).atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()
        return MealEntity(
            userId = 1, description = null, createdAtUtc = t, mealTimeUtc = t, carbohydrates = carbs, proteins = protein, fats = fat,
            impactType = impact, mealType = type, imageId = "", recommendation = null, reasoning = null, sittingId = "s$day-$hour"
        )
    }

    private fun bolus(day: Int, minutesFromMeal: Int, value: Float = 5f, smb: Boolean = false): BolusEntity {
        val t = today.minusDays((27 - day).toLong()).atTime(19, 0).toInstant(ZoneOffset.UTC).toEpochMilli() + minutesFromMeal * 60_000L
        return BolusEntity(userId = 1, timestampUtc = t, createdAtUtc = t, updatedAtUtc = t, value = value, eventType = "Meal Bolus", isSmb = smb, pumpType = "", pumpSerial = "", pumpId = 0)
    }

    private fun detect(days: List<DayGlucose>, meals: List<MealEntity>, boluses: List<BolusEntity> = emptyList()) =
        MealDetector.detect(DetectionInput(days, thresholds, now, zone, meals.toSittings(), boluses))

    private val dinners = (0..22).map { meal(it) }
    private fun gentle(m: Int) = 110f + 30f * sin(PI * m / 120).toFloat().coerceAtLeast(0f)

    @Test fun smoothMealsAreFavourable() {
        val found = detect(days { _, m -> if (m <= 120) gentle(m) else 110f }, dinners)
        val good = found.single { it.card.detector == "meal-good" }
        assertEquals(PatternTone.FAVORABLE, good.card.tone)
        assertEquals("meal.good.meals", good.card.id)
        assertTrue(found.none { it.card.tone == PatternTone.UNFAVORABLE })
    }

    @Test fun recurringPostMealLowsAreFound() {
        val lowMeals = setOf(1, 4, 6, 9, 12, 15, 18, 20)
        val d = days { day, m -> if (day in lowMeals && m in 90..180) 58f else if (m <= 120) gentle(m) else 110f }
        val low = detect(d, dinners).single { it.card.detector == "meal-low" }
        assertEquals(8, low.card.positiveCount)
        assertEquals(23, low.card.sampleSize)
        assertEquals("meals", low.card.sampleUnit)
        assertTrue(low.card.miniGraph is uk.scimone.diafit.patterns.domain.MiniGraph.EventResponse)
        assertTrue(low.card.cases.isNotEmpty())
        // Examples are anchored on their meal and mark the low point inside the shaded 1-4 h after it.
        for (c in low.card.cases) {
            assertNotNull(c.eventUtc)
            assertTrue(c.markUtc!! in c.focusFromUtc..c.focusToUtc)
            assertEquals(c.eventUtc!! + 60 * 60_000L, c.focusFromUtc)
        }
    }

    @Test fun oneBadMealIsNotAPattern() {
        val d = days { day, m -> if (day == 5 && m in 90..180) 55f else if (m <= 120) gentle(m) else 110f }
        assertTrue(detect(d, dinners).none { it.card.detector == "meal-low" })
    }

    @Test fun bigRisesAfterMealsAreFound() {
        val d = days { _, m -> if (m <= 120) 110f + 100f * sin(PI * m / 120).toFloat() else 110f }
        val spike = detect(d, dinners).single { it.card.detector == "meal-spike" }
        assertTrue(spike.card.effectSize >= 90)
        assertEquals(PatternTone.UNFAVORABLE, spike.card.tone)
    }

    @Test fun glucoseStayingHighHoursLaterIsFound() {
        val d = days { _, m -> if (m < 150) gentle(m) else 200f }
        assertTrue(detect(d, dinners).any { it.card.detector == "meal-slow" })
    }

    @Test fun tooFewMealsGivesNothing() {
        assertTrue(detect(days { _, m -> gentle(m) }, (0..4).map { meal(it) }).isEmpty())
    }

    @Test fun overlappingMealsAreLeftOut() {
        // A snack 90 minutes after every dinner: neither response is clean, so nothing is left to analyse.
        val meals = (0..22).flatMap { listOf(meal(it), meal(it, 20, 30, type = MealType.SNACK)) }
        assertTrue(detect(days { _, m -> 110f + 100f * sin(PI * m / 120).toFloat().coerceAtLeast(0f) }, meals).isEmpty())
    }

    @Test fun mealsWithPoorSensorCoverageAreLeftOut() {
        val d = days(coverage = { _, bin -> bin % 3 == 0 || bin < dinnerBin }) { _, m -> 110f + 100f * sin(PI * m / 120).toFloat().coerceAtLeast(0f) }
        assertTrue(detect(d, dinners).isEmpty())
    }

    @Test fun mealsThatStartLowAreTreatedAsLowTreatmentAndIgnored() {
        val spike = { m: Int -> 110f + 100f * sin(PI * m / 120).toFloat().coerceAtLeast(0f) }
        val startsLow = days { _, m -> spike(m) }.map { day ->
            DayGlucose(day.date, FloatArray(BINS_PER_DAY) { if (it in (dinnerBin - 6)..dinnerBin) 60f else day.bins[it] })
        }
        assertTrue(detect(startsLow, dinners).isEmpty())
        assertTrue(detect(days { _, m -> spike(m) }, dinners).isNotEmpty())
    }

    @Test fun richerMealsKeepGlucoseUpLongerUsingRecordedComposition() {
        val rich = (0..22).filter { it % 2 == 0 }.toSet()
        val meals = (0..22).map { if (it in rich) meal(it, protein = 30, fat = 30) else meal(it, protein = 10, fat = 5) }
        val d = days { day, m -> if (m <= 120) gentle(m) else if (day in rich && m in 180..300) 170f else 115f }
        val r = detect(d, meals).single { it.card.detector == "meal-rich" }
        assertTrue(r.card.title.k == "pat_meal_rich_title")
        assertTrue(r.card.limitations.any { it.k == "pat_meal_rich_lim_comp" })
    }

    @Test fun withoutRecordedCompositionLoggedAbsorptionIsUsedAndLabelled() {
        val slow = (0..22).filter { it % 2 == 0 }.toSet()
        val meals = (0..22).map { meal(it, impact = if (it in slow) ImpactType.LONG else ImpactType.SHORT) }
        val d = days { day, m -> if (m <= 120) gentle(m) else if (day in slow && m in 180..300) 170f else 115f }
        val r = detect(d, meals).single { it.card.detector == "meal-rich" }
        assertTrue(r.card.title.k == "pat_meal_slowabs_title")
        assertTrue(r.card.limitations.any { it.k == "pat_meal_rich_lim_logged" })
    }

    @Test fun earlierInsulinWithSmallerRisesIsReportedCarefully() {
        val early = (0..22).filter { it % 2 == 0 }.toSet()
        val boluses = (0..22).map { bolus(it, if (it in early) -15 else 0) }
        val d = days { day, m -> if (m <= 120) 110f + (if (day in early) 25f else 75f) * sin(PI * m / 120).toFloat() else 110f }
        val b = detect(d, dinners, boluses).single { it.card.detector == "meal-bolus" }
        assertEquals(PatternTone.MIXED, b.card.tone)
        assertTrue(b.card.limitations.any { it.k == "pat_meal_bolus_lim" })
    }

    @Test fun smbsDoNotCountAsMealInsulin() {
        val early = (0..22).filter { it % 2 == 0 }.toSet()
        val boluses = (0..22).map { bolus(it, if (it in early) -15 else 0, smb = true) }
        val d = days { day, m -> if (m <= 120) 110f + (if (day in early) 25f else 75f) * sin(PI * m / 120).toFloat() else 110f }
        assertTrue(detect(d, dinners, boluses).none { it.card.detector == "meal-bolus" })
    }

    @Test fun richIsRelativeToTheUsersOwnMeals() {
        // Fat + protein of 10..30 g (below any fixed 35 g cut): the richer half is still compared, using this person's median.
        val rich = (0..22).filter { it % 2 == 0 }.toSet()
        val meals = (0..22).map { if (it in rich) meal(it, protein = 15, fat = 15) else meal(it, protein = 4, fat = 4) }
        val d = days { day, m -> if (m <= 120) gentle(m) else if (day in rich && m in 180..300) 170f else 115f }
        val r = detect(d, meals).single { it.card.detector == "meal-rich" }
        assertTrue(r.card.limitations.any { it.k == "pat_meal_rich_lim_comp" && it.a[0] == "30" })
    }

    @Test fun effectSizeThresholdsFollowTheUsersVariability() {
        fun whole(f: (Int, Int) -> Float) = (0 until 28).map { d -> DayGlucose(today.minusDays((27 - d).toLong()), FloatArray(BINS_PER_DAY) { f(d, it) }) }
        val steady = DetectionInput(whole { _, _ -> 110f }, thresholds, now, zone)
        assertEquals(0.7 * 20.0, steady.scaled(20.0), 0.001) // no variability at all: the smallest scale
        val wobbly = DetectionInput(whole { d, bin -> if ((d + bin) % 2 == 0) 60f else 260f }, thresholds, now, zone)
        assertEquals(1.4 * 20.0, wobbly.scaled(20.0), 0.001) // very variable: capped at 1.4x
        val few = DetectionInput(emptyList(), thresholds, now, zone)
        assertEquals(20.0, few.scaled(20.0), 0.001)          // too little data: the base value
    }
}
