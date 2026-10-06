package uk.scimone.diafit.settings.domain.usecase

import uk.scimone.diafit.core.domain.repository.MealAnalysisRepository

class ListAiModelsUseCase(
    private val repository: MealAnalysisRepository
) {
    suspend operator fun invoke(): Result<List<String>> = repository.listModels()
}
