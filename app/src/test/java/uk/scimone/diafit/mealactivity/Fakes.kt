package uk.scimone.diafit.mealactivity

import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.mealactivity.data.*

/** In-memory persisted world with the same semantics as the Room DAO (publish/promote atomicity, fingerprints, user scoping). */
class FakeWorld {
    val meals = ArrayList<MealEntity>()
    val readings = ArrayList<CgmEntity>()
    var onReadings: (() -> Unit)? = null
    var failReadingsOnce = false
    val batchSizes = ArrayList<Int>()

    val source = object : ActivitySource {
        private fun live(userId: Int) = meals.filter { it.userId == userId && it.isValid && it.mergedIntoId == null }
        override suspend fun mealRevisionRows(userId: Int) = live(userId).sortedBy { it.id }.map {
            MealRevisionRow(it.id, it.mealTimeUtc, it.carbohydrates, it.proteins, it.fats, it.impactType.name, it.sittingId, it.components.size)
        }
        override suspend fun cgmRevision(userId: Int, before: Long): CgmRevision {
            val r = readings.filter { it.userId == userId && it.timestamp < before }
            return CgmRevision(r.size, r.minOfOrNull { it.timestamp } ?: 0, r.maxOfOrNull { it.timestamp } ?: 0, r.sumOf { it.valueMgdl.toLong() }, r.sumOf { it.valueMgdl * ((it.timestamp / 60000) % 977) })
        }
        override suspend fun meals(userId: Int, fromUtc: Long, toUtc: Long) = live(userId).filter { it.mealTimeUtc in fromUtc..toUtc }.sortedBy { it.mealTimeUtc }
        override suspend fun mealBatch(userId: Int, afterTime: Long, afterId: Int, limit: Int): List<MealEntity> {
            val page = live(userId).sortedWith(compareBy({ it.mealTimeUtc }, { it.id }))
                .filter { it.mealTimeUtc > afterTime || (it.mealTimeUtc == afterTime && it.id > afterId) }.take(limit)
            batchSizes += page.size
            return page
        }
        override suspend fun readings(userId: Int, fromUtc: Long, toUtc: Long): List<CgmEntity> {
            if (failReadingsOnce) { failReadingsOnce = false; throw java.io.IOException("disk error") }
            onReadings?.let { onReadings = null; it() }
            return readings.filter { it.userId == userId && it.timestamp in fromUtc..toUtc }.sortedBy { it.timestamp }
        }
    }

    val curves = ArrayList<MealActivityEntity>()
    val models = ArrayList<PersonalActivityModelEntity>()
    val states = HashMap<Int, ActivityPipelineStateEntity>()
    var nextId = 1L
    var publishCount = 0

    val store = object : ActivityStore {
        override suspend fun getCurrent(mealId: Int) = curves.firstOrNull { it.mealId == mealId && it.status == CurveStatus.CURRENT }
        override suspend fun publish(e: MealActivityEntity) {
            val existing = curves.indexOfFirst { it.mealId == e.mealId && it.inputFingerprint == e.inputFingerprint }
            if (existing >= 0 && curves[existing].status == CurveStatus.CURRENT) return
            publishCount++
            for (i in curves.indices) if (curves[i].mealId == e.mealId && curves[i].status == CurveStatus.CURRENT) curves[i] = curves[i].copy(status = CurveStatus.SUPERSEDED)
            if (existing >= 0) curves[existing] = curves[existing].copy(status = CurveStatus.CURRENT) else curves += e.copy(id = nextId++)
            val sup = curves.filter { it.mealId == e.mealId && it.status == CurveStatus.SUPERSEDED }.sortedByDescending { it.generatedAtUtc * 1000 + it.id }
            sup.drop(1).forEach { curves.remove(it) }
        }
        override suspend fun invalidate(mealId: Int) { for (i in curves.indices) if (curves[i].mealId == mealId && curves[i].status == CurveStatus.CURRENT) curves[i] = curves[i].copy(status = CurveStatus.INVALID) }
        override suspend fun invalidateOrphans(userId: Int): Int {
            val live = meals.filter { it.userId == userId && it.isValid && it.mergedIntoId == null }.map { it.id }.toSet()
            var n = 0
            for (i in curves.indices) if (curves[i].userId == userId && curves[i].status == CurveStatus.CURRENT && curves[i].mealId !in live) { curves[i] = curves[i].copy(status = CurveStatus.INVALID); n++ }
            return n
        }
        override suspend fun activeModel(userId: Int) = models.firstOrNull { it.userId == userId && it.status == ModelStatus.ACTIVE }
        override suspend fun latestRejected(userId: Int) = models.filter { it.userId == userId && it.status == ModelStatus.REJECTED }.maxByOrNull { it.version }
        override suspend fun maxModelVersion(userId: Int) = models.filter { it.userId == userId }.maxOfOrNull { it.version } ?: 0
        override suspend fun promote(model: PersonalActivityModelEntity, expectedActiveVersion: Int?): Int? {
            if (activeModel(model.userId)?.version != expectedActiveVersion) return null
            for (i in models.indices) if (models[i].userId == model.userId && models[i].status == ModelStatus.ACTIVE) models[i] = models[i].copy(status = ModelStatus.SUPERSEDED)
            models += model.copy(status = ModelStatus.ACTIVE, id = nextId++)
            return model.version
        }
        override suspend fun retireActive(userId: Int, expectedActiveVersion: Int): Boolean {
            if (activeModel(userId)?.version != expectedActiveVersion) return false
            for (i in models.indices) if (models[i].userId == userId && models[i].status == ModelStatus.ACTIVE) models[i] = models[i].copy(status = ModelStatus.SUPERSEDED)
            return true
        }
        override suspend fun recordRejected(model: PersonalActivityModelEntity) { models += model.copy(id = nextId++) }
        override suspend fun restorePrevious(userId: Int): Int? {
            val prev = models.filter { it.userId == userId && it.status == ModelStatus.SUPERSEDED }.maxByOrNull { it.version } ?: return null
            for (i in models.indices) if (models[i].userId == userId && models[i].status == ModelStatus.ACTIVE) models[i] = models[i].copy(status = ModelStatus.SUPERSEDED)
            models[models.indexOf(prev)] = prev.copy(status = ModelStatus.ACTIVE)
            return prev.version
        }
        override suspend fun state(userId: Int) = states[userId]
        override suspend fun putState(s: ActivityPipelineStateEntity) { states[s.userId] = s }
    }

    fun current(mealId: Int) = curves.filter { it.mealId == mealId && it.status == CurveStatus.CURRENT }
}
