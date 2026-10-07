package uk.scimone.diafit.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.ActivityData
import uk.scimone.diafit.core.domain.repository.ActivityRepository

class ObserveActivitySinceUseCase(private val repository: ActivityRepository) {
    operator fun invoke(start: Long, userId: Int): Flow<ActivityData> = repository.observeSince(start, userId)
}

class GetActivityBetweenUseCase(private val repository: ActivityRepository) {
    suspend operator fun invoke(start: Long, end: Long, userId: Int): ActivityData =
        repository.getBetween(start, end, userId)
}
