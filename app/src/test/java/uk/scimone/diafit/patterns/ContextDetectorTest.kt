package uk.scimone.diafit.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.GlucoseThresholds
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.model.MealType
import uk.scimone.diafit.core.domain.model.toSittings
import uk.scimone.diafit.patterns.domain.ActivityEvent
import uk.scimone.diafit.patterns.domain.BINS_PER_DAY
import uk.scimone.diafit.patterns.domain.ContextDetector
import uk.scimone.diafit.patterns.domain.DayGlucose
import uk.scimone.diafit.patterns.domain.DetectionInput
import uk.scimone.diafit.patterns.domain.MiniGraph
import uk.scimone.diafit.patterns.domain.PatternCandidate
import uk.scimone.diafit.patterns.domain.PatternCard
import uk.scimone.diafit.patterns.domain.Msg
import uk.scimone.diafit.patterns.domain.PatternCategory
import uk.scimone.diafit.patterns.domain.PatternPipeline
import uk.scimone.diafit.patterns.domain.PatternTone
import uk.scimone.diafit.patterns.domain.SleepNight
import uk.scimone.diafit.patterns.domain.TipTopic
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ContextDetectorTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 14)
    private val now = today.atTime(23, 59).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val thresholds = GlucoseThresholds(low = 70, high = 180)
    private fun date(d: Int) = today.minusDays((27 - d).toLong())
    private fun ms(d: Int, hour: Int, minute: Int = 0) = date(d).atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** Glucose by (day, minutes since [hour]:00 of that day); 110 when [f] says null-ish via NaN-free default. */
    private fun days(hour: Int, f: (Int, Int) -> Float?) = (0 until 28).map { d ->
        DayGlucose(date(d), FloatArray(BINS_PER_DAY) { bin -> f(d, bin * 5 - hour * 60) ?: 110f })
    }

    private fun meal(d: Int, hour: Int, carbs: Int = 60, protein: Int = 0, fat: Int = 0) = MealEntity(
        userId = 1, description = null, createdAtUtc = ms(d, hour), mealTimeUtc = ms(d, hour), carbohydrates = carbs, proteins = protein, fats = fat,
        impactType = ImpactType.MEDIUM, mealType = MealType.LUNCH, imageId = "", recommendation = null, reasoning = null, sittingId = "s$d-$hour"
    )

    /** Glucose rise curve after a meal: up to [peak] at 60 min, back to 0 at 180 min. */
    private fun bump(m: Int, peak: Float) = when {
        m < 0 -> 0f; m <= 60 -> peak * m / 60f; m <= 180 -> peak * (180 - m) / 120f; else -> 0f
    }

    private fun detect(days: List<DayGlucose>, meals: List<MealEntity> = emptyList(), events: List<ActivityEvent> = emptyList()) =
        ContextDetector.detect(DetectionInput(days, thresholds, now, zone, meals.toSittings(), emptyList(), events))

    @Test fun mealsAtOneTimeOfDayRisingMoreAreFound() {
        val meals = (0 until 27).flatMap { listOf(meal(it, 8), meal(it, 19)) }
        val g = (0 until 28).map { d ->
            DayGlucose(date(d), FloatArray(BINS_PER_DAY) { bin ->
                val m = bin * 5
                110f + bump(m - 8 * 60, 30f + (d % 3)) + bump(m - 19 * 60, 100f + (d % 3))
            })
        }
        val found = detect(g, meals).map { it.card.id }
        assertTrue(found.toString(), found.any { it.startsWith("ctx.mealtime") })
    }

    @Test fun mealsAfterActivityRisingLessIsFavourable() {
        val meals = (0 until 27).map { meal(it, 12) }
        val events = (0 until 27 step 2).map { ActivityEvent(ms(it, 10), ms(it, 11), "Activity", false) }
        val g = days(12) { d, m -> 110f + bump(m, (if (d % 2 == 0) 40f else 100f) + (d % 3)) }
        val c = detect(g, meals, events).single { it.card.id == "ctx.mealactivity" }
        assertEquals(PatternTone.FAVORABLE, c.card.tone)
        assertEquals(PatternCategory.COMBINED, c.card.category)
        assertTrue(c.card.miniGraph is MiniGraph.EventResponse)
    }

    @Test fun weekendMealsRisingMoreAreFound() {
        fun weekend(d: Int) = date(d).dayOfWeek.let { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }
        val meals = (0 until 27).map { meal(it, 12) }
        val g = days(12) { d, m -> 110f + bump(m, (if (weekend(d)) 110f else 40f) + (d % 3)) }
        assertTrue(detect(g, meals).any { it.card.id == "ctx.mealweekend" })
    }

    @Test fun richMealsWithASecondRiseAreFoundAtMatchedCarbs() {
        fun rich(d: Int) = d % 2 == 0
        val meals = (0 until 27).map { meal(it, 12, 60, protein = if (rich(it)) 20 else 5, fat = if (rich(it)) 30 else 0) }
        val g = days(12) { d, m ->
            110f + when {
                m < 0 -> 0f
                rich(d) -> when { m <= 60 -> 40f * m / 60; m <= 120 -> 40f - 20f * (m - 60) / 60; m <= 200 -> 20f + 60f * (m - 120) / 80; else -> 80f - 80f * (m - 200).coerceAtMost(60) / 60 }
                else -> when { m <= 60 -> 40f * m / 60; m <= 150 -> 40f - 40f * (m - 60) / 90; else -> 0f }
            }.let { it + (d % 3) }
        }
        val c = detect(g, meals).single { it.card.id == "ctx.mealdelay" }
        assertEquals(PatternTone.UNFAVORABLE, c.card.tone)
    }

    @Test fun glucoseAlreadyFallingAtTheStartOfActivityIsFound() {
        val events = (0 until 27).map { ActivityEvent(ms(it, 10), ms(it, 10, 40), "Running", true) }
        val g = days(10) { d, m ->
            if (d % 2 == 0) when { m < -30 -> 160f; m < 0 -> 130f - m.toFloat(); m <= 40 -> 130f - 50f * m / 40; else -> 80f }
            else 130f
        }
        assertTrue(detect(g, events = events).any { it.card.id == "ctx.expre" })
    }

    @Test fun steadierNightsAfterActiveDaysAreFavourable() {
        val events = (0 until 27 step 2).map { ActivityEvent(ms(it, 10), ms(it, 10, 45), "Activity", false) }
        // Night (bins 0..71) of the day after an active (even) day (odd day) stays in range; after quiet days (even day) half of it is high.
        val g = (0 until 28).map { d -> DayGlucose(date(d), FloatArray(BINS_PER_DAY) { bin -> if (bin < 72 && d % 2 == 0 && bin % 2 == 0) 230f else 120f }) }
        val c = detect(g, events = events).single { it.card.id == "ctx.night" }
        assertEquals(PatternTone.FAVORABLE, c.card.tone)
    }

    @Test fun nothingWithoutVariation() {
        val meals = (0 until 27).map { meal(it, 12) }
        assertTrue(detect(days(12) { _, m -> 110f + bump(m, 50f) }, meals).isEmpty())
    }

    private fun nightOf(d: Int, asleep: Int, bed: Int = 120) = SleepNight(date(d), ms(d, 7) - asleep * 60_000L, ms(d, 7), asleep, null, bed)

    @Test fun shortSleepMattersMoreAfterQuietDaysIsFound() {
        // d % 4: 0 short+active, 1 long+active, 2 short+quiet, 3 long+quiet. Only short+quiet is followed by a bad day.
        val nights = (0 until 27).map { nightOf(it, if (it % 2 == 0) 330 else 480) }
        val events = (1 until 27).filter { (it % 4) < 2 }.map { ActivityEvent(ms(it - 1, 10), ms(it - 1, 11), "Activity", false) }
        val g = (0 until 28).map { d -> DayGlucose(date(d), FloatArray(BINS_PER_DAY) { if (d % 4 == 2 && it >= 96) 230f else 120f }) }
        val c = ContextDetector.detect(DetectionInput(g, thresholds, now, zone, events = events, nights = nights)).single { it.card.id == "ctx.sleepactivity" }
        assertEquals(PatternTone.UNFAVORABLE, c.card.tone)
    }

    @Test fun fullRegularNightsAfterActiveDaysAreFavourable() {
        val nights = (0 until 27).map { nightOf(it, if (it % 3 != 0) 480 else 330) }
        val events = (1 until 27).filter { it % 2 == 1 }.map { ActivityEvent(ms(it - 1, 10), ms(it - 1, 11), "Activity", false) }
        fun good(d: Int) = d in 1 until 27 && d % 3 != 0 && d % 2 == 1
        val g = (0 until 28).map { d -> DayGlucose(date(d), FloatArray(BINS_PER_DAY) { if (!good(d) && it >= 96 && it % 2 == 0) 230f else 120f }) }
        val c = ContextDetector.detect(DetectionInput(g, thresholds, now, zone, events = events, nights = nights)).single { it.card.id == "ctx.sleepgood" }
        assertEquals(PatternTone.FAVORABLE, c.card.tone)
    }

    @Test fun lateRichDinnersKeepingNightsHigherAreFound() {
        fun late(d: Int) = d % 2 == 1
        fun rich(d: Int) = d % 4 == 1
        val meals = (0 until 27).map { meal(it, if (late(it)) 22 else 19, 60, protein = if (late(it) && rich(it)) 20 else 5, fat = if (late(it) && rich(it)) 30 else 0) }
        val g = (0 until 28).map { d -> DayGlucose(date(d), FloatArray(BINS_PER_DAY) { if (it < 72 && d > 0 && late(d - 1) && rich(d - 1)) 190f else 110f }) }
        val found = ContextDetector.detect(DetectionInput(g, thresholds, now, zone, meals.toSittings())).map { it.card.id }
        assertTrue(found.toString(), "ctx.latedinnerrich" in found)
    }

    private fun candidate(id: String, tone: PatternTone) = PatternCandidate(
        PatternCard(
            id = id, category = PatternCategory.MEAL, detector = "x", title = Msg(id), summary = Msg(""), miniGraph = MiniGraph.ProfileComparison(Msg("a"), emptyList(), Msg("b"), emptyList()),
            observationFromUtc = 0, observationToUtc = 0, sampleSize = 1, sampleUnit = "meals", effectSize = 1.0, effectLabel = Msg(""), uncertainty = Msg(""), limitations = emptyList(),
            tone = tone, priority = 1.0
        ),
        TipTopic.MEAL_RICH
    )

    @Test fun overlappingCardsAreMergedIntoTheMoreInformativeOne() {
        val kept = PatternPipeline.withoutOverlaps(
            listOf(candidate("meal.rich", PatternTone.UNFAVORABLE), candidate("ctx.mealdelay", PatternTone.UNFAVORABLE),
                candidate("meal.type.dinner", PatternTone.UNFAVORABLE), candidate("ctx.mealtime.evening", PatternTone.UNFAVORABLE))
        ).map { it.card.id }
        assertEquals(listOf("meal.rich", "ctx.mealtime.evening"), kept)
        // A different tone is not the same finding.
        assertFalse(PatternPipeline.withoutOverlaps(listOf(candidate("ctx.night", PatternTone.UNFAVORABLE), candidate("ex.nextday", PatternTone.FAVORABLE))).size < 2)
    }
}
