package uk.scimone.diafit.core.data.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import uk.scimone.diafit.core.data.repository.syncsource.bolussyncsource.AapsCarbParser
import uk.scimone.diafit.core.domain.repository.MealRepository
import uk.scimone.diafit.core.domain.usecase.MergeCarbEntriesUseCase
import uk.scimone.diafit.settings.domain.repository.SettingsRepository
import uk.scimone.diafit.settings.domain.model.Connector
import uk.scimone.diafit.settings.domain.model.DataType

/** Inserts carb entries from AAPS treatment broadcasts as (photo-less) meals, deduped by `sourceId`. */
class CarbBroadcastWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val mealRepository: MealRepository by inject()
    private val mergeCarbEntries: MergeCarbEntriesUseCase by inject()
    private val settings: SettingsRepository by inject()

    override suspend fun doWork(): Result {
        if (settings.getSelection(DataType.FOOD) != Connector.AAPS) {
            Log.d(TAG, "Carb intent ignored: AAPS isn't the selected bolus/treatment source.")
            return Result.success()
        }
        val meals = AapsCarbParser.parse(reconstructIntent(inputData))
        var inserted = 0
        for (meal in meals) {
            if (meal.sourceId != null && mealRepository.existsBySourceId(meal.sourceId)) continue
            // The same treatment can arrive twice, once without `_id` (fallback sourceId) and once with it.
            if (mealRepository.existsImportedAt(meal.mealTimeUtc, meal.carbohydrates)) continue
            if (mealRepository.createMeal(meal).isFailure) return Result.retry()
            inserted++
        }
        // Fold the entries into meals already logged in the app (photo + AI estimate).
        if (inserted > 0) runCatching { mergeCarbEntries(meals.first().userId) }
        Log.d(TAG, "Carb entries parsed=${meals.size}, inserted=$inserted")
        return Result.success()
    }

    private fun reconstructIntent(data: Data): Intent {
        val intent = Intent(data.getString("action"))
        data.keyValueMap.forEach { (key, value) ->
            if (key != "action") when (value) {
                is Int -> intent.putExtra(key, value)
                is Float -> intent.putExtra(key, value)
                is Long -> intent.putExtra(key, value)
                is String -> intent.putExtra(key, value)
                is Boolean -> intent.putExtra(key, value)
            }
        }
        return intent
    }

    private companion object {
        const val TAG = "CarbInsertWorker"
    }
}
