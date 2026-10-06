package uk.scimone.diafit.core.domain.usecase

import android.net.Uri
import uk.scimone.diafit.core.domain.model.MealAnalysisResult
import uk.scimone.diafit.core.domain.repository.MealAnalysisRepository

class AnalyzeMealUseCase(
    private val repository: MealAnalysisRepository
) {
    suspend operator fun invoke(imageUri: Uri): Result<MealAnalysisResult> =
        repository.analyzeMealPhoto(imageUri)
}
