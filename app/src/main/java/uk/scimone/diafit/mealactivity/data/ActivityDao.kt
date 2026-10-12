package uk.scimone.diafit.mealactivity.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import uk.scimone.diafit.core.domain.model.MealEntity

/** Source-of-truth reads for the pipeline (meals and CGM stay owned by their own DAOs). */
@Dao
abstract class MealActivityDao {
    // ---- curves ----
    @Query("SELECT * FROM MealActivityEntity WHERE mealId = :mealId AND status = 'CURRENT' LIMIT 1")
    abstract suspend fun getCurrent(mealId: Int): MealActivityEntity?

    @Query("SELECT * FROM MealActivityEntity WHERE userId = :userId AND mealId IN (:mealIds) AND status = 'CURRENT'")
    abstract suspend fun getCurrent(userId: Int, mealIds: List<Int>): List<MealActivityEntity>

    @Query("SELECT * FROM MealActivityEntity WHERE mealId = :mealId AND inputFingerprint = :fingerprint LIMIT 1")
    protected abstract suspend fun findByFingerprint(mealId: Int, fingerprint: String): MealActivityEntity?

    @Query("UPDATE MealActivityEntity SET status = 'SUPERSEDED' WHERE mealId = :mealId AND status = 'CURRENT'")
    protected abstract suspend fun supersedeCurrent(mealId: Int)

    @Query("UPDATE MealActivityEntity SET status = 'CURRENT', generatedAtUtc = :now WHERE id = :id")
    protected abstract suspend fun reactivate(id: Long, now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(e: MealActivityEntity): Long

    @Query("DELETE FROM MealActivityEntity WHERE mealId = :mealId AND status = 'SUPERSEDED' AND id NOT IN (SELECT id FROM MealActivityEntity WHERE mealId = :mealId AND status = 'SUPERSEDED' ORDER BY generatedAtUtc DESC, id DESC LIMIT 1)")
    protected abstract suspend fun pruneSuperseded(mealId: Int)

    /** Atomically makes [e] the meal's current curve; the previous one stays as the single superseded revision. Idempotent. */
    @Transaction
    open suspend fun publish(e: MealActivityEntity) {
        val existing = findByFingerprint(e.mealId, e.inputFingerprint)
        if (existing != null && existing.status == CurveStatus.CURRENT) return
        supersedeCurrent(e.mealId)
        if (existing != null) reactivate(existing.id, e.generatedAtUtc) else insert(e)
        pruneSuperseded(e.mealId)
    }

    @Query("UPDATE MealActivityEntity SET status = 'INVALID' WHERE mealId = :mealId AND status = 'CURRENT'")
    abstract suspend fun invalidate(mealId: Int)

    /** Curves whose meal is gone, deleted or merged into another entry are no longer current. */
    @Query("UPDATE MealActivityEntity SET status = 'INVALID' WHERE userId = :userId AND status = 'CURRENT' AND mealId NOT IN (SELECT id FROM MealEntity WHERE userId = :userId AND isValid = 1 AND mergedIntoId IS NULL)")
    abstract suspend fun invalidateOrphans(userId: Int): Int

    @Query("DELETE FROM MealActivityEntity WHERE mealId NOT IN (SELECT id FROM MealEntity)")
    abstract suspend fun deleteForRemovedMeals(): Int

    @Query("SELECT COUNT(*) FROM MealActivityEntity WHERE userId = :userId AND status = 'CURRENT'")
    abstract suspend fun countCurrent(userId: Int): Int

    // ---- models ----
    @Query("SELECT * FROM PersonalActivityModelEntity WHERE userId = :userId AND status = 'ACTIVE' LIMIT 1")
    abstract suspend fun activeModel(userId: Int): PersonalActivityModelEntity?

    @Query("SELECT * FROM PersonalActivityModelEntity WHERE userId = :userId AND status = 'REJECTED' ORDER BY version DESC LIMIT 1")
    abstract suspend fun latestRejected(userId: Int): PersonalActivityModelEntity?

    @Query("SELECT * FROM PersonalActivityModelEntity WHERE userId = :userId AND status = 'SUPERSEDED' ORDER BY version DESC LIMIT 1")
    abstract suspend fun latestSuperseded(userId: Int): PersonalActivityModelEntity?

    @Query("SELECT COALESCE(MAX(version), 0) FROM PersonalActivityModelEntity WHERE userId = :userId")
    abstract suspend fun maxModelVersion(userId: Int): Int

    @Insert abstract suspend fun insertModel(m: PersonalActivityModelEntity): Long

    @Query("UPDATE PersonalActivityModelEntity SET status = :status WHERE userId = :userId AND status = 'ACTIVE'")
    protected abstract suspend fun setActiveStatus(userId: Int, status: String)

    @Query("UPDATE PersonalActivityModelEntity SET status = 'ACTIVE' WHERE id = :id")
    protected abstract suspend fun activate(id: Long)

    @Query("DELETE FROM PersonalActivityModelEntity WHERE userId = :userId AND status != 'ACTIVE' AND version NOT IN (SELECT version FROM PersonalActivityModelEntity WHERE userId = :userId AND status != 'ACTIVE' ORDER BY version DESC LIMIT 3)")
    protected abstract suspend fun pruneModels(userId: Int)

    /**
     * Publishes [model] as the active one only if the active version is still [expectedActiveVersion] (null = none), so a
     * stale job cannot overwrite a newer promotion. Returns the new version, or null when the check failed.
     */
    @Transaction
    open suspend fun promote(model: PersonalActivityModelEntity, expectedActiveVersion: Int?): Int? {
        if (activeModel(model.userId)?.version != expectedActiveVersion) return null
        setActiveStatus(model.userId, ModelStatus.SUPERSEDED)
        insertModel(model.copy(status = ModelStatus.ACTIVE))
        pruneModels(model.userId)
        return model.version
    }

    /** Falls back to the baseline (no active model); the retired model stays as the previous revision. */
    @Transaction
    open suspend fun retireActive(userId: Int, expectedActiveVersion: Int): Boolean {
        if (activeModel(userId)?.version != expectedActiveVersion) return false
        setActiveStatus(userId, ModelStatus.SUPERSEDED)
        return true
    }

    /** Rollback: the most recent superseded model becomes active again (the current one, if any, is superseded). */
    @Transaction
    open suspend fun restorePrevious(userId: Int): Int? {
        val previous = latestSuperseded(userId) ?: return null
        setActiveStatus(userId, ModelStatus.SUPERSEDED)
        activate(previous.id)
        return previous.version
    }

    // ---- state ----
    @Query("SELECT * FROM ActivityPipelineStateEntity WHERE userId = :userId")
    abstract suspend fun state(userId: Int): ActivityPipelineStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putState(s: ActivityPipelineStateEntity)

    // ---- source revision (cheap aggregates over the source-of-truth tables) ----
    @Query("SELECT id, mealTimeUtc, carbohydrates, proteins, fats, impactType, sittingId, length(components) AS componentsLength FROM MealEntity WHERE userId = :userId AND isValid = 1 AND mergedIntoId IS NULL ORDER BY id")
    abstract suspend fun mealRevisionRows(userId: Int): List<MealRevisionRow>

    @Query("SELECT COUNT(*) AS n, COALESCE(MIN(timestamp), 0) AS minTs, COALESCE(MAX(timestamp), 0) AS maxTs, COALESCE(SUM(valueMgdl), 0) AS sumValue, COALESCE(SUM(valueMgdl * ((timestamp / 60000) % 977)), 0) AS weighted FROM CgmEntity WHERE userId = :userId AND timestamp < :before")
    abstract suspend fun cgmRevision(userId: Int, before: Long): CgmRevision

    // ---- keyset-paged reads for batch recalculation ----
    @Query("SELECT * FROM MealEntity WHERE userId = :userId AND isValid = 1 AND mergedIntoId IS NULL AND mealTimeUtc >= :fromTime AND (mealTimeUtc > :afterTime OR (mealTimeUtc = :afterTime AND id > :afterId)) ORDER BY mealTimeUtc ASC, id ASC LIMIT :limit")
    abstract suspend fun mealBatch(userId: Int, fromTime: Long, afterTime: Long, afterId: Int, limit: Int): List<MealEntity>
}

data class MealRevisionRow(
    val id: Int, val mealTimeUtc: Long, val carbohydrates: Int, val proteins: Int?, val fats: Int?,
    val impactType: String, val sittingId: String?, val componentsLength: Int
)

data class CgmRevision(val n: Int, val minTs: Long, val maxTs: Long, val sumValue: Long, val weighted: Long)
