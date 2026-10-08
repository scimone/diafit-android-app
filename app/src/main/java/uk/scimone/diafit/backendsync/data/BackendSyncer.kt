package uk.scimone.diafit.backendsync.data

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.scimone.diafit.backendsync.domain.MealIn
import uk.scimone.diafit.backendsync.domain.backendSourceId
import uk.scimone.diafit.backendsync.domain.toBackend
import uk.scimone.diafit.backendsync.domain.toBackendSession
import uk.scimone.diafit.backendsync.domain.toPatch

/**
 * Uploads what is new since the previous sync to the configured backend:
 * - CGM, boluses, heart rate: rows after the last uploaded local id, in pages, via the idempotent bulk endpoints;
 * - sleep sessions: those with stage rows newer than the last upload (re-imports re-send, the backend skips them);
 * - meals: new ones in bulk; edited or deleted ones (soft delete = `is_valid: false`) are found on the backend by
 *   their source id and patched, since bulk uploads never update.
 * Steps, workouts and pump events have no endpoint and stay local. The first sync uploads the whole history.
 */
class BackendSyncer(
    private val dao: BackendSyncDao,
    private val api: BackendApi,
    private val store: BackendSyncStore
) {
    private val lock = Mutex()

    /** Null when no backend is configured. */
    suspend fun sync(userId: Int): BackendResult<String>? = lock.withLock {
        val config = store.config.value
        if (!config.isConfigured) return null
        store.setRunning(true)
        val now = System.currentTimeMillis()
        try {
            val counts = linkedMapOf<String, Int>()
            val result = runSteps(config, userId, counts)
            val summary = counts.filterValues { it > 0 }.entries.joinToString(", ") { "${it.value} ${it.key}" }
                .ifEmpty { "nothing new" }
            when (result) {
                is BackendResult.Ok -> store.recordResult(true, "Uploaded $summary", now)
                is BackendResult.Failed -> store.recordResult(false, result.message, now)
            }
            Log.i(TAG, "Sync: $summary ${(result as? BackendResult.Failed)?.message.orEmpty()}")
            return if (result is BackendResult.Failed) result else BackendResult.Ok(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            store.recordResult(false, "Sync failed (${e.javaClass.simpleName})", now)
            throw e
        } finally {
            store.setRunning(false)
        }
    }

    private suspend fun runSteps(config: BackendConfig, userId: Int, counts: MutableMap<String, Int>): BackendResult<Unit> {
        uploadPaged(SyncCursor.CGM, counts, "readings",
            fetch = { dao.cgmAfter(userId, it, PAGE) }, id = { it.id },
            upload = { rows -> api.uploadCgm(config, rows.mapNotNull { it.toBackend() }) }
        ).failure()?.let { return it }
        uploadPaged(SyncCursor.BOLUS, counts, "boluses",
            fetch = { dao.bolusesAfter(userId, it, PAGE) }, id = { it.id },
            upload = { rows -> api.uploadBoluses(config, rows.mapNotNull { it.toBackend() }) }
        ).failure()?.let { return it }
        syncMeals(config, userId, counts).failure()?.let { return it }
        uploadPaged(SyncCursor.HEART_RATE, counts, "heart rates",
            fetch = { dao.heartRatesAfter(userId, it, PAGE) }, id = { it.id },
            upload = { rows -> api.uploadHeartRates(config, rows.mapNotNull { it.toBackend() }) }
        ).failure()?.let { return it }
        return syncSleep(config, userId, counts)
    }

    private suspend fun <T> uploadPaged(
        cursor: SyncCursor,
        counts: MutableMap<String, Int>,
        label: String,
        fetch: suspend (afterId: Int) -> List<T>,
        id: (T) -> Int,
        upload: suspend (List<T>) -> BackendResult<BulkResult>
    ): BackendResult<Unit> {
        var after = store.cursor(cursor)
        while (true) {
            val rows = fetch(after)
            if (rows.isEmpty()) return BackendResult.Ok(Unit)
            when (val result = upload(rows)) {
                is BackendResult.Failed -> return result
                is BackendResult.Ok -> counts.merge(label, result.data.inserted, Int::plus)
            }
            after = id(rows.last())
            store.setCursor(cursor, after) // progress survives a failure or a killed worker
            if (rows.size < PAGE) return BackendResult.Ok(Unit)
        }
    }

    private suspend fun syncSleep(config: BackendConfig, userId: Int, counts: MutableMap<String, Int>): BackendResult<Unit> {
        val after = store.cursor(SyncCursor.SLEEP)
        val maxId = dao.maxSleepStageId(userId) ?: return BackendResult.Ok(Unit)
        if (maxId <= after) return BackendResult.Ok(Unit)
        val sessionIds = dao.sleepSessionsChangedAfter(userId, after)
        for (chunk in sessionIds.chunked(SLEEP_PAGE)) {
            val sessions = dao.sleepStages(chunk).groupBy { it.sessionId }.values.mapNotNull { it.toBackendSession() }
            when (val result = api.uploadSleepSessions(config, sessions)) {
                is BackendResult.Failed -> return result
                is BackendResult.Ok -> counts.merge("sleep sessions", result.data.inserted, Int::plus)
            }
        }
        store.setCursor(SyncCursor.SLEEP, maxId)
        return BackendResult.Ok(Unit)
    }

    private suspend fun syncMeals(config: BackendConfig, userId: Int, counts: MutableMap<String, Int>): BackendResult<Unit> {
        val uploaded = store.uploadedMeals()
        val toCreate = ArrayList<Pair<String, MealIn>>()
        var updated = 0
        for (meal in dao.allMeals(userId)) {
            val key = meal.backendSourceId()
            val payload = meal.toBackend()
            val previous = uploaded[key]
            when {
                previous == null -> if (payload.isValid) toCreate += key to payload // never uploaded, deleted: skip
                previous.hash == payload.hashCode() -> Unit
                else -> when (val found = api.findMeal(config, key, previous.mealTimeUtc)) {
                    is BackendResult.Failed -> return found
                    is BackendResult.Ok -> {
                        val remote = found.data
                        if (remote == null) {
                            toCreate += key to payload // gone from the backend: upload it again
                        } else {
                            api.updateMeal(config, remote.id, payload.toPatch()).failure()?.let { return it }
                            store.setUploadedMeals(mapOf(key to UploadedMeal(payload.hashCode(), meal.mealTimeUtc)))
                            updated++
                        }
                    }
                }
            }
        }
        for (chunk in toCreate.chunked(PAGE)) {
            when (val result = api.uploadMeals(config, chunk.map { it.second })) {
                is BackendResult.Failed -> return result
                is BackendResult.Ok -> counts.merge("meals", result.data.inserted, Int::plus)
            }
            store.setUploadedMeals(chunk.associate { (key, p) -> key to UploadedMeal(p.hashCode(), java.time.Instant.parse(p.mealTimeUtc).toEpochMilli()) })
        }
        if (updated > 0) counts["meal updates"] = updated
        return BackendResult.Ok(Unit)
    }

    private fun <T> BackendResult<T>.failure(): BackendResult.Failed? = this as? BackendResult.Failed

    private companion object {
        const val TAG = "BackendSync"
        /** Rows per bulk request (the API accepts up to 10,000). */
        const val PAGE = 2000
        /** Sleep sessions carry up to 2,000 stages each, so fewer per request. */
        const val SLEEP_PAGE = 50
    }
}
