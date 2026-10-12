package uk.scimone.diafit.mealactivity.data

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.mealactivity.domain.*
import java.util.concurrent.ConcurrentHashMap

/** Outcome of one pipeline run, for logging and job status. */
data class RunReport(
    val result: String,
    val message: String,
    val needsRerun: Boolean = false,
    val curvesWritten: Int = 0,
    val curvesChecked: Int = 0,
    val episodes: Int = 0,
    val modelVersion: Int? = null
)

/** A curve as served to a consumer, with how it was made and whether it still matches the current inputs/model. */
data class ServedActivity(
    val series: ActivitySeries,
    val method: String,
    val modelVersion: Int?,
    val algorithmVersion: Int,
    val quality: String,
    val stale: Boolean,
    val generatedAtUtc: Long
)

/**
 * The pipeline. Source of truth = persisted meals and CGM. Per user, strictly serialised, idempotent and revision-aware:
 *
 * 1. cheap source revision (meals + settled CGM history) and algorithm key decide whether a full pass is due;
 * 2. training episodes are rebuilt from persisted data; their revision decides whether a refit is due;
 * 3. a fitted candidate is promoted only if validated and only if the source revision is unchanged since the run began;
 * 4. every meal's curve is recomputed in keyset-paged batches, publishing only curves whose input fingerprint changed.
 *
 * Nothing here holds a database transaction while fitting; each publish is a small atomic step.
 */
class MealActivityService(
    private val source: ActivitySource,
    private val store: ActivityStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val grid: CurveGrid = CurveGrid(),
    private val params: ActivityParams = ActivityParams(),
    private val cfg: PersonalizationConfig = PersonalizationConfig(),
    private val rules: TrainingRules = TrainingRules(),
    private val batchSize: Int = 200,
    private val publishToRuntime: Boolean = true,
    private val log: (String) -> Unit = { Log.i(TAG, it) }
) : MealActivityRefresher {

    private val locks = ConcurrentHashMap<Int, Mutex>()
    private val json = Json { ignoreUnknownKeys = true }
    private fun lock(userId: Int) = locks.getOrPut(userId) { Mutex() }

    /** Version constants plus a hash of the effective parameters/rules, so editing a default without bumping a version still recomputes. */
    private fun algoKey() = ActivityVersions.algoKey(grid) + "-" + Fingerprint.of(params.hashCode(), cfg.hashCode(), rules.hashCode()).take(6)
    private fun engineOf(model: PersonalActivityModelEntity?): MealActivityEngine =
        MealActivityEngine(params, model?.let { json.decodeFromString<PersonalAdjust>(it.adjustJson) } ?: PersonalAdjust(), grid, cfg)

    /** Loads the active model into the process-wide engine (call at app start). */
    suspend fun loadActive(userId: Int) {
        val m = store.activeModel(userId)?.takeIf { it.algoKey == algoKey() }
        if (publishToRuntime) MealActivity.set(ActiveEngine(engineOf(m), m?.version, m?.datasetRevision))
    }

    // ------------------------------------------------------------------------------------------------------------
    // Immediate path: a just-logged/edited meal gets its curve now, with the model in force.
    // ------------------------------------------------------------------------------------------------------------

    override suspend fun refresh(meal: MealEntity) {
        val model = store.activeModel(meal.userId)?.takeIf { it.algoKey == algoKey() }
        publishIfChanged(meal, engineOf(model), model)
    }

    /** The curve to show for [meal]: the stored current one (stale-flagged if outdated) or a freshly computed baseline. */
    suspend fun served(meal: MealEntity): ServedActivity? {
        val model = store.activeModel(meal.userId)?.takeIf { it.algoKey == algoKey() }
        val engine = engineOf(model)
        val input = meal.toMealInput()
        val row = store.getCurrent(meal.id)
        if (row != null) {
            val stale = row.inputFingerprint != curveFingerprint(input, engine, model?.version)
            return ServedActivity(row.toSeries(), row.method, row.modelVersion, row.algorithmVersion, row.quality, stale, row.generatedAtUtc)
        }
        val curve = engine.curve(input) ?: return null
        val q = if (curve.lowerConfidenceReasons.isEmpty()) QUALITY_STANDARD else QUALITY_LOWER
        return ServedActivity(curve.sample(grid), methodOf(model), model?.version, ActivityVersions.ALGORITHM, q, true, clock())
    }

    // ------------------------------------------------------------------------------------------------------------
    // Background pass.
    // ------------------------------------------------------------------------------------------------------------

    suspend fun runForUser(userId: Int, force: Boolean = false): RunReport = lock(userId).withLock { runLocked(userId, force) }

    private suspend fun sourceRevision(userId: Int, now: Long): String {
        // Today's and yesterday's CGM is still arriving every minute and cannot change a curve or an older episode, so only
        // settled history is part of the revision (the periodic full pass picks up the recent meals once their window closes).
        val settledBefore = (now / DAY_MS) * DAY_MS - DAY_MS
        val meals = source.mealRevisionRows(userId)
        val cgm = source.cgmRevision(userId, settledBefore)
        return Fingerprint.of(meals.map { listOf(it.id, it.mealTimeUtc, it.carbohydrates, it.proteins, it.fats, it.impactType, it.sittingId, it.componentsLength) }, cgm.n, cgm.minTs, cgm.maxTs, cgm.sumValue, cgm.weighted)
    }

    private suspend fun runLocked(userId: Int, force: Boolean): RunReport {
        val started = clock()
        val key = algoKey()
        val rev = sourceRevision(userId, started)
        val st = store.state(userId)
        val due = force || st == null || st.processedRevision != rev || st.processedAlgoKey != key || started - st.lastFullRunUtc >= FULL_PASS_INTERVAL_MS
        if (!due) return RunReport("SKIPPED", "source revision unchanged").also { log("user=$userId skipped (revision $rev unchanged)") }
        log("user=$userId full pass start revision=$rev algo=$key force=$force")

        store.invalidateOrphans(userId)

        // --- training set (rebuilt from persisted data) and model decision ---
        val meals = source.meals(userId, started - rules.windowDays * DAY_MS - DAY_MS, started)
        val set = TrainingExampleBuilder.buildAll(meals, started, grid, rules) { a, b -> source.readings(userId, a, b) }
        val trainingRev = Fingerprint.of(set.revision, key, cfg.hashCode(), rules.hashCode(), params.hashCode())
        var active = store.activeModel(userId)
        var message = "episodes=${set.examples.size} excluded=${set.excluded}"
        var wrote = 0

        if (active != null && active.algoKey != key) {
            log("user=$userId retiring model v${active.version}: algorithm changed (${active.algoKey} -> $key)")
            store.retireActive(userId, active.version); active = null
        }

        if (active?.datasetRevision == trainingRev) {
            message += "; model v${active.version} up to date"
        } else if (store.latestRejected(userId)?.datasetRevision == trainingRev) {
            message += "; this data was already evaluated and rejected"
        } else {
            when (val outcome = PersonalTrainer.train(set.examples, params, grid, cfg)) {
                is TrainOutcome.Ineligible -> {
                    message += "; not eligible: ${outcome.reason}"
                    if (active != null) {
                        log("user=$userId retiring model v${active.version}: no longer eligible (${outcome.reason})")
                        store.retireActive(userId, active.version); active = null
                    }
                }
                is TrainOutcome.Rejected -> {
                    message += "; candidate rejected: ${outcome.reason}"
                    log("user=$userId candidate rejected: ${outcome.reason}")
                    store.recordRejected(modelRow(userId, store.maxModelVersion(userId) + 1, ModelStatus.REJECTED, PersonalAdjust(), trainingRev, set, outcome.report, outcome.reason, started))
                }
                is TrainOutcome.Accepted -> {
                    // Do not publish a model fitted on a superseded dataset: re-check the revision just before publishing.
                    if (sourceRevision(userId, clock()) != rev) {
                        log("user=$userId source changed during training; candidate discarded, rerun needed")
                        return RunReport("STALE", "source changed during training", needsRerun = true, episodes = set.examples.size)
                    }
                    val version = store.maxModelVersion(userId) + 1
                    val promoted = store.promote(modelRow(userId, version, ModelStatus.ACTIVE, outcome.adjust, trainingRev, set, outcome.report, "accepted", started), active?.version)
                    if (promoted == null) {
                        log("user=$userId promotion lost a race with a newer model; rerun needed")
                        return RunReport("STALE", "model changed concurrently", needsRerun = true, episodes = set.examples.size)
                    }
                    active = store.activeModel(userId)
                    message += "; promoted model v$version ${outcome.adjust}"
                    log("user=$userId promoted model v$version ${outcome.adjust} holdout base=${outcome.report.baselineHoldout} cand=${outcome.report.candidateHoldout}")
                }
            }
        }

        if (publishToRuntime) MealActivity.set(ActiveEngine(engineOf(active), active?.version, active?.datasetRevision))

        // --- recalculate curves in bounded keyset pages ---
        val engine = engineOf(active)
        var checked = 0
        var afterTime = Long.MIN_VALUE; var afterId = Int.MIN_VALUE
        while (true) {
            val page = source.mealBatch(userId, afterTime, afterId, batchSize)
            if (page.isEmpty()) break
            for (m in page) { if (publishIfChanged(m, engine, active)) wrote++; checked++ }
            afterTime = page.last().mealTimeUtc; afterId = page.last().id
            yield()
        }

        val finished = clock()
        val changedMeanwhile = sourceRevision(userId, finished) != rev
        store.putState(
            ActivityPipelineStateEntity(
                userId, processedRevision = rev, processedAlgoKey = key, lastFullRunUtc = started, lastRunUtc = finished,
                lastResult = if (changedMeanwhile) "RERUN" else "OK", lastMessage = message,
                eligibleEpisodes = set.examples.size, curvesRecalculated = wrote, curvesTotal = checked
            )
        )
        log("user=$userId pass done: $wrote of $checked curves rewritten; $message${if (changedMeanwhile) "; source changed meanwhile" else ""}")
        return RunReport(if (changedMeanwhile) "RERUN" else "OK", message, changedMeanwhile, wrote, checked, set.examples.size, active?.version)
    }

    private fun methodOf(model: PersonalActivityModelEntity?) = if (model != null) Method.PERSONALIZED else Method.BASELINE

    private fun modelRow(userId: Int, version: Int, status: String, adjust: PersonalAdjust, trainingRev: String, set: TrainingSet, report: TrainReport, note: String, now: Long) =
        PersonalActivityModelEntity(
            userId = userId, version = version, status = status, adjustJson = json.encodeToString(adjust), datasetRevision = trainingRev,
            algoKey = algoKey(), trainedAtUtc = now, episodeCount = set.examples.size, reportJson = json.encodeToString(report), note = note
        )

    /** Writes the meal's curve if its fingerprint differs from the stored current one. Returns true when it wrote. */
    private suspend fun publishIfChanged(meal: MealEntity, engine: MealActivityEngine, model: PersonalActivityModelEntity?): Boolean {
        val input = meal.toMealInput()
        val curve = engine.curve(input)
        if (curve == null) { store.invalidate(meal.id); return false }
        val fp = curveFingerprint(input, engine, model?.version)
        val current = store.getCurrent(meal.id)
        if (current?.inputFingerprint == fp) return false
        val series = curve.sample(grid)
        val reasons = curve.lowerConfidenceReasons
        store.publish(
            MealActivityEntity(
                userId = meal.userId, mealId = meal.id, mealTimeUtc = meal.mealTimeUtc, algorithmVersion = ActivityVersions.ALGORITHM,
                method = methodOf(model), modelVersion = model?.version, trainingRevision = model?.datasetRevision, inputFingerprint = fp,
                quality = if (reasons.isEmpty()) QUALITY_STANDARD else QUALITY_LOWER, qualityNotes = reasons.joinToString("; ").ifEmpty { null },
                horizonMinutes = grid.horizonMinutes, stepMinutes = grid.stepMinutes, activityWindowHours = params.activityWindowHours,
                carbsG = meal.carbohydrates.toDouble(),
                carbsRemainingG = MealActivityEntity.csv(series.carbsRemainingG)!!, carbsRateGph = MealActivityEntity.csv(series.carbsRateGph)!!,
                proteinRemainingG = MealActivityEntity.csv(series.proteinRemainingG), proteinActivityGph = MealActivityEntity.csv(series.proteinActivityGph),
                fatRemainingG = MealActivityEntity.csv(series.fatRemainingG), fatActivityGph = MealActivityEntity.csv(series.fatActivityGph),
                generatedAtUtc = clock(), status = CurveStatus.CURRENT
            )
        )
        return true
    }

    companion object {
        const val TAG = "MealActivity"
        const val QUALITY_STANDARD = "STANDARD"
        const val QUALITY_LOWER = "LOWER_CONFIDENCE"
        private const val DAY_MS = 86_400_000L
        /** Even with no revision change, recent meals become training episodes once their window has elapsed. */
        const val FULL_PASS_INTERVAL_MS = 6 * 3_600_000L
    }
}
