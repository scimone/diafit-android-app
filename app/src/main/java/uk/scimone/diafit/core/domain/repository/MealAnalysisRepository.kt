package uk.scimone.diafit.core.domain.repository

import android.net.Uri
import uk.scimone.diafit.core.domain.model.MealAnalysisResult

interface MealAnalysisRepository {
    /** Analyses all [imageUris] together as one course. */
    suspend fun analyzeMealPhotos(imageUris: List<Uri>): Result<MealAnalysisResult>
    suspend fun listModels(): Result<List<String>>
}
