package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.model.BolusEntity
import uk.scimone.diafit.core.domain.repository.BolusRepository

class InsertBolusUseCase (
    private val repository: BolusRepository
) {
    suspend operator fun invoke(bolusEntity: BolusEntity) {
        repository.insertBolus(bolusEntity)
    }
}