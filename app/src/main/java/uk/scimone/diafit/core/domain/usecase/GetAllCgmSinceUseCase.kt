package uk.scimone.diafit.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.repository.CgmRepository


class GetAllCgmSinceUseCase (
    private val repository: CgmRepository
) {
    operator fun invoke(startTime: Long, userId: Int): Flow<List<CgmEntity>> {
        return repository.getAllCgmSince(startTime, userId)
    }
}