package uk.scimone.diafit.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.repository.CgmRepository

class GetLatestCgmUseCase(
    private val repository: CgmRepository
) {
    operator fun invoke(): Flow<CgmEntity?> {
        return repository.getLatestCgm()
    }
}
