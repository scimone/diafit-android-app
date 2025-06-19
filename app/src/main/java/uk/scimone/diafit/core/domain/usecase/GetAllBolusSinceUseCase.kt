package uk.scimone.diafit.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.repository.BolusRepository

class GetAllBolusSinceUseCase(
    private val repository: BolusRepository
) {
    operator fun invoke(start: Long): Flow<List<BolusEntity>> {
        return repository.getAllBolusSince(start)
    }
}