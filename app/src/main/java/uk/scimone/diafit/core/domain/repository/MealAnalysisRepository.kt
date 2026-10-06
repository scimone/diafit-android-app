package uk.scimone.diafit.core.domain.repository

import android.net.Uri
import uk.scimone.diafit.core.domain.model.MealAnalysisResult

interface MealAnalysisRepository {
    suspend fun analyzeMealPhoto(imageUri: Uri): Result<MealAnalysisResult>
    suspend fun listModels(): Result<List<String>>
}
