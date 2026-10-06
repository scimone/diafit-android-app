package uk.scimone.diafit.core.domain.usecase

import android.util.Log
import uk.scimone.diafit.core.domain.model.MealMatcher
import uk.scimone.diafit.core.domain.repository.MealRepository

/**
 * Keeps one entry per meal when the same carbs arrive twice: logged in the app (photo, AI estimate)
 * and imported from AAPS (what was actually dosed). The logged meal stays; the imported row is hidden
 * (`mergedIntoId`, kept in the table so the import isn't re-created) and its carbs become the meal's
 * carbs, with the original estimate kept in `estimatedCarbs`. See [MealMatcher] for the matching rules.
 */
class MergeCarbEntriesUseCase(private val mealRepository: MealRepository) {

    /** Merges every confident match among recent entries; returns how many were merged. */
    suspend operator fun invoke(userId: Int, now: Long = System.currentTimeMillis()): Int {
        val recent = mealRepository.getUnmergedSince(userId, now - LOOKBACK_MS)
        val matches = MealMatcher.findMatches(recent).filter { it.confident }
        matches.forEach { merge(it) }
        if (matches.isNotEmpty()) Log.d(TAG, "Merged ${matches.size} imported carb entries into logged meals")
        return matches.size
    }

    /** Merges one match (also used when the user confirms a suggested one). */
    suspend fun merge(match: MealMatcher.Match): Result<Unit> {
        val logged = mealRepository.getMealById(match.logged.id) ?: return Result.failure(IllegalStateException("meal gone"))
        val imported = mealRepository.getMealById(match.imported.id) ?: return Result.failure(IllegalStateException("entry gone"))
        val master = if (match.sittingLevel) {
            // One dose for several courses: just link, the courses keep their own carbs.
            logged.copy(aapsLinked = true)
        } else {
            logged.copy(carbohydrates = imported.carbohydrates, estimatedCarbs = logged.carbohydrates, aapsLinked = true)
        }
        return mealRepository.updateMeal(imported.copy(mergedIntoId = logged.id))
            .mapCatching { mealRepository.updateMeal(master).getOrThrow() }
    }

    /** The user says these are two different things: never suggest or merge the entry again. */
    suspend fun keepSeparate(importedId: Int): Result<Unit> {
        val imported = mealRepository.getMealById(importedId) ?: return Result.success(Unit)
        return mealRepository.updateMeal(imported.copy(mergeDeclined = true))
    }

    /** Undoes a merge: the imported entry reappears on its own and the meal gets its estimate back. */
    suspend fun unlink(masterId: Int): Result<Unit> {
        val master = mealRepository.getMealById(masterId) ?: return Result.success(Unit)
        mealRepository.getMergedInto(masterId).forEach { imported ->
            mealRepository.updateMeal(imported.copy(mergedIntoId = null, mergeDeclined = true)).onFailure { return Result.failure(it) }
        }
        return mealRepository.updateMeal(master.copy(carbohydrates = master.estimatedCarbs ?: master.carbohydrates, estimatedCarbs = null, aapsLinked = false))
    }

    private companion object {
        const val TAG = "MergeCarbEntries"
        const val LOOKBACK_MS = 3 * 24 * 60 * 60_000L
    }
}

