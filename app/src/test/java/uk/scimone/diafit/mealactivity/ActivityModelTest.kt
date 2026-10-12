package uk.scimone.diafit.mealactivity

import org.junit.Assert.*
import org.junit.Test
import uk.scimone.diafit.core.domain.model.ImpactType
import uk.scimone.diafit.mealactivity.domain.*

class ActivityModelTest {
    private val engine = MealActivityEngine()
    private fun input(c: Double?, p: Double? = 0.0, f: Double? = 0.0, fiber: Double? = null, speed: AbsorptionSpeed = AbsorptionSpeed.MEDIUM) =
        MealInput(c, p, f, fiber, speed, T0)

    @Test fun zeroCarbsGiveNoCarbActivity() {
        val c = engine.curve(input(0.0))!!
        for (m in 0..720 step 15) { assertEquals(0.0, c.carbsRateGph(m.toDouble()), 0.0); assertEquals(0.0, c.carbsRemainingG(m.toDouble()), 0.0) }
        assertTrue(c.lowerConfidenceReasons.contains("no carbohydrate"))
        assertEquals(0.0, c.carbsEndMinute(), 0.0)
    }

    @Test fun boundaryAndMonotonicSemantics() {
        val c = engine.curve(input(60.0))!!
        assertEquals(60.0, c.carbsRemainingG(0.0), 1e-9)
        assertEquals(0.0, c.carbsRateGph(0.0), 1e-9)
        var prev = 60.0
        for (m in 1..1440) {
            val rem = c.carbsRemainingG(m.toDouble())
            assertTrue("remaining non-increasing at $m", rem <= prev + 1e-4)
            assertTrue(rem >= -1e-4 && rem <= 60.0 + 1e-4)
            assertTrue("rate >= 0", c.carbsRateGph(m.toDouble()) >= 0.0)
            prev = rem
        }
        assertEquals(0.0, c.carbsRemainingG(1440.0), 1e-3)
    }

    @Test fun remainingIsConsistentWithIntegratedRate() {
        val c = engine.curve(input(80.0, 20.0, 30.0))!!
        var absorbed = 0.0
        for (m in 0 until 1440) absorbed += c.carbsRateGph(m + 0.5) / 60.0 // g per minute
        assertEquals(80.0, absorbed, 0.8) // total absorbed ≈ carbs, never more
        var cum = 0.0
        for (m in 0 until 300) cum += c.carbsRateGph(m + 0.5) / 60.0
        assertEquals(80.0 - c.carbsRemainingG(300.0), cum, 0.8)
    }

    @Test fun rateAndRemainingAndActiveAreDistinctQuantities() {
        val c = engine.curve(input(60.0))!!
        val t = 50.0
        assertNotEquals(c.carbsRateGph(t), c.carbsRemainingG(t), 1.0)
        assertEquals(c.carbsRateGph(t) * ActivityParams().activityWindowHours, c.carbsActiveG(t), 1e-9)
    }

    @Test fun scalesLinearlyWithCarbs() {
        val a = engine.curve(input(30.0))!!; val b = engine.curve(input(90.0))!!
        for (m in listOf(30.0, 60.0, 120.0)) assertEquals(3 * a.carbsRateGph(m), b.carbsRateGph(m), 1e-3)
    }

    @Test fun fatAndProteinSlowAndDelayTheCarbCurve() {
        val plain = engine.curve(input(60.0, 0.0, 0.0))!!
        val rich = engine.curve(input(60.0, 30.0, 45.0))!!
        assertTrue(rich.carbOnsetMin > plain.carbOnsetMin)
        assertTrue(rich.carbScaleMin > plain.carbScaleMin)
        assertTrue(rich.carbsEndMinute() > plain.carbsEndMinute())
        // Same carbs absorb: peak rate is lower for the broader curve.
        val peak = { c: MealActivityCurve -> (0..600).maxOf { c.carbsRateGph(it.toDouble()) } }
        assertTrue(peak(rich) < peak(plain))
    }

    @Test fun combinedSlowingHasDiminishingReturns() {
        val e = engine
        val f = e.slowing(input(50.0, 0.0, 40.0)); val p = e.slowing(input(50.0, 40.0, 0.0)); val both = e.slowing(input(50.0, 40.0, 40.0))
        assertTrue(both < f + p)
        assertTrue(both > maxOf(f, p))
    }

    @Test fun absorptionSpeedChangesTiming() {
        val fast = engine.curve(input(50.0, speed = AbsorptionSpeed.FAST))!!; val slow = engine.curve(input(50.0, speed = AbsorptionSpeed.SLOW))!!
        assertTrue(fast.carbsEndMinute() < slow.carbsEndMinute())
    }

    @Test fun missingMacrosStayMissing() {
        val c = engine.curve(input(50.0, null, null))!!
        assertNull(c.proteinRemainingG(30.0)); assertNull(c.fatActivityGph(30.0))
        assertTrue(c.lowerConfidenceReasons.containsAll(listOf("protein unknown", "fat unknown")))
        val known = engine.curve(input(50.0, 20.0, 10.0))!!
        assertEquals(20.0, known.proteinRemainingG(0.0)!!, 1e-9)
        assertTrue(known.fatActivityGph(240.0)!! > 0.0)
        // Unknown is not treated as zero: the same meal with 0 g fat is faster than... equal to the unknown (neither slows).
        assertEquals(engine.curve(input(50.0, 0.0, 0.0))!!.carbScaleMin, c.carbScaleMin, 1e-9)
    }

    @Test fun invalidInputsAreRejected() {
        assertNull(engine.curve(input(null)))
        assertNull(engine.curve(input(-1.0)))
        assertNull(engine.curve(input(Double.NaN)))
        assertNull(engine.curve(input(1e6)))
        assertNull(engine.curve(input(50.0, -5.0)))
        assertNull(engine.curve(input(50.0, 10.0, Double.POSITIVE_INFINITY)))
        assertNull(engine.curve(input(50.0, 10.0, 10.0, fiber = 1000.0)))
    }

    @Test fun residualActivityAtHorizonIsExposed() {
        val slowRich = engine.curve(input(80.0, 40.0, 80.0, 10.0, AbsorptionSpeed.SLOW))!!
        val series = slowRich.sample(CurveGrid())
        assertTrue("some carbohydrate remains at 6 h", series.carbsRemainingG.last() > 0.0)
        assertTrue(series.carbsResidualShareAtHorizon(80.0) in 0.0..1.0)
        val fast = engine.curve(input(30.0, speed = AbsorptionSpeed.FAST))!!.sample(CurveGrid())
        assertTrue(fast.carbsResidualShareAtHorizon(30.0) < series.carbsResidualShareAtHorizon(80.0))
    }

    @Test fun gridBoundariesAndDeterminism() {
        val g = CurveGrid()
        assertEquals(25, g.size)
        val s1 = engine.curve(input(45.0, 12.0, 20.0))!!.sample(g)
        val s2 = MealActivityEngine().curve(input(45.0, 12.0, 20.0))!!.sample(g)
        assertArrayEquals(s1.carbsRateGph, s2.carbsRateGph, 0.0)
        assertEquals(0.0, s1.carbsRateGph[0], 1e-9); assertEquals(45.0, s1.carbsRemainingG[0], 1e-9)
        assertEquals(s1.carbsRateGph[4], s1.carbsActiveG(4) / ActivityParams().activityWindowHours, 1e-9)
    }

    @Test fun extremeMealsStayFiniteAndBounded() {
        val c = engine.curve(input(500.0, 300.0, 300.0, 100.0, AbsorptionSpeed.SLOW))!!
        for (m in 0..1440 step 5) {
            assertTrue(c.carbsRateGph(m.toDouble()).isFinite()); assertTrue(c.carbsRemainingG(m.toDouble()) in -1e-3..500.0 + 1e-3)
        }
    }

    @Test fun personalAdjustChangesTimingOnly() {
        val base = engine.curve(input(60.0))!!
        val slow = MealActivityEngine(adjust = PersonalAdjust(timeScale = 1.5, onsetShiftMin = 10.0)).curve(input(60.0))!!
        assertTrue(slow.carbsEndMinute() > base.carbsEndMinute())
        assertEquals(60.0, slow.carbsRemainingG(0.0), 1e-9)
    }

    @Test fun fingerprintChangesWithInputsAndModelNotWithRepeat() {
        val i = input(50.0, 10.0, 10.0)
        val a = curveFingerprint(i, engine)
        assertEquals(a, curveFingerprint(i, MealActivityEngine()))
        assertNotEquals(a, curveFingerprint(i.copy(carbsG = 51.0), engine))
        assertNotEquals(a, curveFingerprint(i, MealActivityEngine(adjust = PersonalAdjust(timeScale = 1.2))))
        assertNotEquals(a, curveFingerprint(i, MealActivityEngine(params = ActivityParams(carbShape = 3.0))))
    }

    @Test fun impactTypeMapsToSpeed() {
        assertEquals(AbsorptionSpeed.FAST, ImpactType.SHORT.toSpeed()); assertEquals(AbsorptionSpeed.SLOW, ImpactType.LONG.toSpeed())
    }

    @Test fun importedCarbEntryMacrosAreUnknownNotZero() {
        val imported = meal(1, T0, 40, protein = 0, fat = 0, sourceId = "ns-1").toMealInput()
        assertNull(imported.proteinG); assertNull(imported.fatG)
        val logged = meal(2, T0, 40, protein = 0, fat = 0).toMealInput()
        assertEquals(0.0, logged.proteinG!!, 0.0)
    }
}
