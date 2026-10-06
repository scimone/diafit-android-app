package uk.scimone.diafit.core.domain.repository

import android.net.Uri
import uk.scimone.diafit.core.domain.model.MealAnalysisResult

interface MealAnalysisRepository {
    /** Analyses all [imageUris] together as one course; [userNotes] is the user's own context (e.g. "I only ate half"). */
    suspend fun analyzeMealPhotos(imageUris: List<Uri>, userNotes: String? = null): Result<MealAnalysisResult>
    suspend fun listModels(): Result<List<String>>
}
