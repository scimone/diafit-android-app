package uk.scimone.diafit.mealactivity.data

import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.model.MealEntity

/** Read-only view of the persisted source data. Implemented over Room; faked in tests. */
interface ActivitySource {
    suspend fun mealRevisionRows(userId: Int): List<MealRevisionRow>
    suspend fun cgmRevision(userId: Int, before: Long): CgmRevision
    /** Valid, unmerged meals with time in [fromUtc, toUtc], oldest first. */
    suspend fun meals(userId: Int, fromUtc: Long, toUtc: Long): List<MealEntity>
    /** Keyset page of valid, unmerged meals ordered by (time, id) after the given position. */
    suspend fun mealBatch(userId: Int, afterTime: Long, afterId: Int, limit: Int): List<MealEntity>
    suspend fun readings(userId: Int, fromUtc: Long, toUtc: Long): List<CgmEntity>
}

/** Persistence of curves, models and job state. */
interface ActivityStore {
    suspend fun getCurrent(mealId: Int): MealActivityEntity?
    suspend fun publish(e: MealActivityEntity)
    suspend fun invalidate(mealId: Int)
    suspend fun invalidateOrphans(userId: Int): Int
    suspend fun activeModel(userId: Int): PersonalActivityModelEntity?
    suspend fun latestRejected(userId: Int): PersonalActivityModelEntity?
    suspend fun maxModelVersion(userId: Int): Int
    suspend fun promote(model: PersonalActivityModelEntity, expectedActiveVersion: Int?): Int?
    suspend fun retireActive(userId: Int, expectedActiveVersion: Int): Boolean
    suspend fun recordRejected(model: PersonalActivityModelEntity)
    suspend fun restorePrevious(userId: Int): Int?
    suspend fun state(userId: Int): ActivityPipelineStateEntity?
    suspend fun putState(s: ActivityPipelineStateEntity)
}

class RoomActivityStore(private val dao: MealActivityDao) : ActivityStore {
    override suspend fun getCurrent(mealId: Int) = dao.getCurrent(mealId)
    override suspend fun publish(e: MealActivityEntity) = dao.publish(e)
    override suspend fun invalidate(mealId: Int) = dao.invalidate(mealId)
    override suspend fun invalidateOrphans(userId: Int): Int { dao.deleteForRemovedMeals(); return dao.invalidateOrphans(userId) }
    override suspend fun activeModel(userId: Int) = dao.activeModel(userId)
    override suspend fun latestRejected(userId: Int) = dao.latestRejected(userId)
    override suspend fun maxModelVersion(userId: Int) = dao.maxModelVersion(userId)
    override suspend fun promote(model: PersonalActivityModelEntity, expectedActiveVersion: Int?) = dao.promote(model, expectedActiveVersion)
    override suspend fun retireActive(userId: Int, expectedActiveVersion: Int) = dao.retireActive(userId, expectedActiveVersion)
    override suspend fun recordRejected(model: PersonalActivityModelEntity) { dao.insertModel(model) }
    override suspend fun restorePrevious(userId: Int) = dao.restorePrevious(userId)
    override suspend fun state(userId: Int) = dao.state(userId)
    override suspend fun putState(s: ActivityPipelineStateEntity) = dao.putState(s)
}

class RoomActivitySource(
    private val dao: MealActivityDao,
    private val mealDao: uk.scimone.diafit.core.data.local.MealDao,
    private val cgmDao: uk.scimone.diafit.core.data.local.CgmDao
) : ActivitySource {
    override suspend fun mealRevisionRows(userId: Int) = dao.mealRevisionRows(userId)
    override suspend fun cgmRevision(userId: Int, before: Long) = dao.cgmRevision(userId, before)
    override suspend fun meals(userId: Int, fromUtc: Long, toUtc: Long) = mealDao.getMealsBetween(fromUtc, toUtc + 1, userId)
    override suspend fun mealBatch(userId: Int, afterTime: Long, afterId: Int, limit: Int) = dao.mealBatch(userId, 0L, afterTime, afterId, limit)
    override suspend fun readings(userId: Int, fromUtc: Long, toUtc: Long) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { cgmDao.getEntriesBetween(fromUtc, toUtc, userId) }
}
