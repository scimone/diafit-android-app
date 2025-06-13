package uk.scimone.diafit.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.MealEntity
import uk.scimone.diafit.core.domain.repository.MealRepository


class GetAllMealsSinceUseCase (
    private val repository: MealRepository
) {
    operator fun invoke(startTime: Long, userId: Int): Flow<List<MealEntity>> {
        return repository.getAllMealsSince(startTime, userId)
    }
}