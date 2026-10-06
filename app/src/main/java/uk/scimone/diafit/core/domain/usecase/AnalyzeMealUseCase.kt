package uk.scimone.diafit.core.domain.usecase

import android.net.Uri
import uk.scimone.diafit.core.domain.model.MealAnalysisResult
import uk.scimone.diafit.core.domain.repository.MealAnalysisRepository

class AnalyzeMealUseCase(
    private val repository: MealAnalysisRepository
) {
    suspend operator fun invoke(imageUris: List<Uri>, userNotes: String? = null): Result<MealAnalysisResult> =
        repository.analyzeMealPhotos(imageUris, userNotes)
}
