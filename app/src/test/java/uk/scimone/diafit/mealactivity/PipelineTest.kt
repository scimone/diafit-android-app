package uk.scimone.diafit.mealactivity

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import uk.scimone.diafit.mealactivity.data.*
import uk.scimone.diafit.mealactivity.domain.*

class PipelineTest {
    private val now = T0 + 400 * HOUR
    private val slowPerson = PersonalAdjust(timeScale = 1.45, onsetShiftMin = 12.0)

    private fun service(w: FakeWorld, grid: CurveGrid = CurveGrid(), params: ActivityParams = ActivityParams(), batch: Int = 200, clock: () -> Long = { now }) =
        MealActivityService(w.source, w.store, clock, grid, params, batchSize = batch, publishToRuntime = false, log = {})

    private fun worldWith(n: Int, withCgm: Boolean = true, seed: Long = 1): Pair<FakeWorld, World> {
        val gen = World(slowPerson, seed = seed); gen.generate(n)
        val fw = FakeWorld(); fw.meals += gen.meals; if (withCgm) fw.readings += gen.readings
        return fw to gen
    }

    @Test fun newMealGetsBaselineCurveImmediately() = runBlocking {
        val w = FakeWorld(); val s = service(w)
        val m = meal(1, T0, 60); w.meals += m
        s.refresh(m)
        val row = w.current(1).single()
        assertEquals(Method.BASELINE, row.method); assertNull(row.modelVersion)
        assertEquals(ActivityVersions.ALGORITHM, row.algorithmVersion)
        val series = row.toSeries()
        assertEquals(60.0, series.carbsRemainingG[0], 1e-3)
        assertEquals(25, series.carbsRateGph.size)
        assertNotNull(s.served(m)); assertFalse(s.served(m)!!.stale)
    }

    @Test fun mealsBeforeCgmStayBaselineThenCgmBackfillTriggersPersonalization() = runBlocking {
        val (w, gen) = worldWith(45, withCgm = false)
        val s = service(w)
        val r1 = s.runForUser(1)
        assertEquals("OK", r1.result); assertNull(w.store.activeModel(1))
        assertEquals(45, w.curves.count { it.status == CurveStatus.CURRENT && it.method == Method.BASELINE })

        w.readings += gen.readings // historical CGM imported afterwards
        val r2 = s.runForUser(1)
        assertEquals("OK", r2.result)
        val model = w.store.activeModel(1)
        assertNotNull("model promoted: ${r2.message}", model)
        assertTrue(w.meals.all { m -> w.current(m.id).single().let { it.method == Method.PERSONALIZED && it.modelVersion == model!!.version } })
        assertTrue(r2.curvesWritten >= 45)
    }

    @Test fun cgmBeforeMealsDoesNothingUntilMealsArrive() = runBlocking {
        val (full, gen) = worldWith(45)
        val w = FakeWorld(); w.readings += gen.readings
        val s = service(w)
        val r = s.runForUser(1)
        assertEquals(0, w.curves.size); assertNull(w.store.activeModel(1)); assertTrue(r.message.contains("not eligible"))
        w.meals += gen.meals
        s.runForUser(1)
        assertNotNull(w.store.activeModel(1))
        assertEquals(45, w.curves.count { it.status == CurveStatus.CURRENT })
    }

    @Test fun largeImportInOneBatchIsHandledInBoundedPages() = runBlocking {
        val (w, _) = worldWith(60)
        service(w, batch = 10).runForUser(1)
        assertTrue(w.batchSizes.all { it <= 10 })
        assertTrue(w.batchSizes.size >= 7)
        assertEquals(60, w.curves.count { it.status == CurveStatus.CURRENT })
    }

    @Test fun rerunWithoutChangesIsANoOp() = runBlocking {
        val (w, _) = worldWith(45)
        val s = service(w)
        s.runForUser(1)
        val models = w.models.size; val published = w.publishCount
        val r = s.runForUser(1)
        assertEquals("SKIPPED", r.result)
        val forced = s.runForUser(1, force = true)
        assertEquals(0, forced.curvesWritten); assertEquals(models, w.models.size); assertEquals(published, w.publishCount)
    }

    @Test fun duplicateImportDoesNotDuplicateEpisodesOrCurves() = runBlocking {
        val (w, _) = worldWith(45)
        val s = service(w)
        s.runForUser(1)
        val v = w.store.activeModel(1)!!.version
        w.meals += w.meals.take(10).mapIndexed { i, m -> m.copy(id = 1000 + i, sittingId = "dup$i") } // same meals imported twice
        val r = s.runForUser(1)
        assertTrue(r.episodes <= 45)
        assertEquals(45, r.episodes.coerceAtMost(45))
        assertEquals(55, w.curves.count { it.status == CurveStatus.CURRENT })
        assertTrue(w.store.activeModel(1)!!.version >= v)
    }

    @Test fun editingOneMealRewritesOnlyThatCurve() = runBlocking {
        val (w, _) = worldWith(45)
        val s = service(w)
        s.runForUser(1)
        val i = w.meals.indexOfFirst { it.id == 20 }
        w.meals[i] = w.meals[i].copy(carbohydrates = w.meals[i].carbohydrates + 15)
        val r = s.runForUser(1)
        assertEquals(1, w.curves.count { it.mealId == 20 && it.status == CurveStatus.SUPERSEDED })
        // the edit may also change the training set and thus the model; either way curves stay consistent
        val active = w.store.activeModel(1)
        assertTrue(w.meals.all { m -> w.current(m.id).single().modelVersion == active?.version })
        assertTrue(r.curvesChecked == 45)
        assertEquals(w.meals[i].carbohydrates.toDouble(), w.current(20).single().carbsG, 0.0)
    }

    @Test fun deletedMealCurveBecomesInvalidAndRestoreReactivates() = runBlocking {
        val (w, _) = worldWith(10)
        val s = service(w)
        s.runForUser(1)
        val i = w.meals.indexOfFirst { it.id == 3 }
        w.meals[i] = w.meals[i].copy(isValid = false)
        s.runForUser(1)
        assertTrue(w.current(3).isEmpty()); assertEquals(1, w.curves.count { it.mealId == 3 && it.status == CurveStatus.INVALID })
        w.meals[i] = w.meals[i].copy(isValid = true) // undo
        s.runForUser(1)
        assertEquals(1, w.current(3).size)
        assertEquals(1, w.curves.count { it.mealId == 3 }) // same fingerprint: the old row is reused, no duplicate
    }

    @Test fun algorithmUpgradeRecomputesAllCurvesAndRetiresOldModel() = runBlocking {
        val (w, _) = worldWith(45)
        service(w).runForUser(1)
        val old = w.store.activeModel(1)!!
        // "v2": a different default parameterisation -> different fingerprints, different training revision.
        val upgraded = service(w, params = ActivityParams(carbShape = 3.0))
        val r = upgraded.runForUser(1)
        assertEquals(45, r.curvesWritten)
        assertTrue(w.meals.all { w.current(it.id).single().inputFingerprint.isNotEmpty() })
        val grid2 = service(w, grid = CurveGrid(360, 30)).runForUser(1) // different algorithm key: old model must not carry over
        assertEquals(45, grid2.curvesWritten)
        assertTrue(w.models.none { it.status == ModelStatus.ACTIVE && !it.algoKey.startsWith(ActivityVersions.algoKey(CurveGrid(360, 30))) })
        assertTrue(w.current(1).single().stepMinutes == 30)
        assertNotNull(old)
    }

    @Test fun concurrentRunsForTheSameUserAreSerialised() = runBlocking {
        val (w, _) = worldWith(45)
        val s = service(w)
        val results = (1..4).map { async(kotlinx.coroutines.Dispatchers.Default) { s.runForUser(1) } }.awaitAll()
        assertEquals(1, results.count { it.result == "OK" })
        assertEquals(3, results.count { it.result == "SKIPPED" })
        assertEquals(1, w.models.count { it.status == ModelStatus.ACTIVE })
        assertEquals(45, w.curves.count { it.status == CurveStatus.CURRENT })
    }

    @Test fun sourceChangingDuringTrainingNeverPublishesAStaleModel() = runBlocking {
        val (w, gen) = worldWith(45)
        val s = service(w)
        w.onReadings = { // a backfill commits while the candidate is being trained
            w.meals += meal(500, T0 + 400 * HOUR - 30 * HOUR, 40)
        }
        val r = s.runForUser(1)
        assertEquals("STALE", r.result); assertTrue(r.needsRerun)
        assertNull(w.store.activeModel(1)); assertEquals(0, w.models.size)
        val again = s.runForUser(1)
        assertEquals("OK", again.result)
        assertNotNull(w.store.activeModel(1))
        assertNotNull(gen)
    }

    @Test fun failedJobIsRetryableAndLeavesNoPartialState() = runBlocking {
        val (w, _) = worldWith(45)
        val s = service(w)
        w.failReadingsOnce = true
        try { s.runForUser(1); fail("expected failure") } catch (e: java.io.IOException) { }
        assertNull(w.states[1]); assertEquals(0, w.curves.size); assertEquals(0, w.models.size)
        assertEquals("OK", s.runForUser(1).result)
        assertNotNull(w.store.activeModel(1))
    }

    @Test fun usersAreIsolated() = runBlocking {
        val (w, gen) = worldWith(45)
        val other = gen.meals.take(5).map { it.copy(id = it.id + 5000, userId = 2) }
        w.meals += other
        val s = service(w)
        s.runForUser(1)
        assertTrue(w.curves.none { it.userId == 2 }); assertTrue(w.models.none { it.userId == 2 })
        s.runForUser(2)
        assertTrue(w.curves.filter { it.userId == 2 }.all { it.mealId >= 5000 })
        assertTrue(w.models.none { it.userId == 2 })
        assertNotNull(w.store.activeModel(1)); assertNull(w.store.activeModel(2))
    }

    @Test fun rejectedCandidateIsRememberedAndBaselineKept() = runBlocking {
        val gen = World(PersonalAdjust(), noise = 25.0, seed = 11); gen.generate(45)
        val rnd = java.util.Random(5)
        val w = FakeWorld(); w.meals += gen.meals; w.readings += gen.readings.map { it.copy(valueMgdl = 100 + rnd.nextInt(60)) }
        val s = service(w)
        s.runForUser(1)
        assertNull(w.store.activeModel(1))
        val rejected = w.models.count { it.status == ModelStatus.REJECTED }
        assertTrue(w.curves.all { it.method == Method.BASELINE })
        s.runForUser(1, force = true) // same data: not refitted
        assertEquals(rejected, w.models.count { it.status == ModelStatus.REJECTED })
    }

    @Test fun generatedCurvesNeverChangeTheTrainingRevision() = runBlocking {
        val (w, _) = worldWith(45)
        val s = service(w)
        s.runForUser(1)
        val rev1 = w.store.activeModel(1)!!.datasetRevision
        s.runForUser(1, force = true)
        assertEquals(rev1, w.store.activeModel(1)!!.datasetRevision)
    }

    @Test fun modelRollbackRestoresPreviousRevision() = runBlocking {
        val (w, gen) = worldWith(45)
        val s = service(w)
        s.runForUser(1)
        val first = w.store.activeModel(1)!!
        // new data arrives -> a new model version is promoted
        val more = World(slowPerson, seed = 9); more.generate(10, startUtc = T0 + 45 * 7 * HOUR, firstId = 100)
        w.meals += more.meals; w.readings += more.readings
        service(w, clock = { now + 100 * HOUR }).runForUser(1)
        val second = w.store.activeModel(1)!!
        if (second.version != first.version) {
            assertEquals(first.version, w.store.restorePrevious(1))
            assertEquals(first.version, w.store.activeModel(1)!!.version)
        }
        assertEquals(1, w.models.count { it.status == ModelStatus.ACTIVE })
    }

    @Test fun servedCurveIsFlaggedStaleWhenInputsChanged() = runBlocking {
        val w = FakeWorld(); val s = service(w)
        val m = meal(1, T0, 60); w.meals += m
        s.refresh(m)
        val edited = m.copy(carbohydrates = 90)
        assertTrue(s.served(edited)!!.stale)
        assertEquals(60.0, s.served(edited)!!.series.carbsRemainingG[0], 1e-3) // still the last valid curve
    }

    @Test fun curvesCarryFullProvenance() = runBlocking {
        val (w, _) = worldWith(45)
        service(w).runForUser(1)
        val model = w.store.activeModel(1)!!
        val row = w.current(5).single()
        assertEquals(model.version, row.modelVersion); assertEquals(model.datasetRevision, row.trainingRevision)
        assertTrue(row.inputFingerprint.isNotEmpty()); assertTrue(row.generatedAtUtc > 0)
        assertEquals(Method.PERSONALIZED, row.method); assertEquals(CurveStatus.CURRENT, row.status)
    }
}
