package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.model.CgmEntity
import uk.scimone.diafit.core.domain.repository.CgmRepository

class InsertCgmUseCase (
    private val repository: CgmRepository
    ) {
        suspend operator fun invoke(cgmValue: CgmEntity) {
            return repository.insertCgm(cgmValue)
        }
    }