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
import uk.scimone.diafit.settings.domain.model.BolusSource
import uk.scimone.diafit.settings.domain.usecase.GetBolusSourceUseCase

/** Inserts carb entries from AAPS treatment broadcasts as (photo-less) meals, deduped by `sourceId`. */
class CarbBroadcastWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val mealRepository: MealRepository by inject()
    private val getBolusSourceUseCase: GetBolusSourceUseCase by inject()

    override suspend fun doWork(): Result {
        if (getBolusSourceUseCase() != BolusSource.AAPS) {
            Log.d(TAG, "Carb intent ignored: AAPS isn't the selected bolus/treatment source.")
            return Result.success()
        }
        val meals = AapsCarbParser.parse(reconstructIntent(inputData))
        var inserted = 0
        for (meal in meals) {
            if (meal.sourceId != null && mealRepository.existsBySourceId(meal.sourceId)) continue
            if (mealRepository.createMeal(meal).isFailure) return Result.retry()
            inserted++
        }
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
