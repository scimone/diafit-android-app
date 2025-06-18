package uk.scimone.diafit.core.domain.repository

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.BolusEntity

interface BolusRepository {

    suspend fun insertBolus(bolusEntity: BolusEntity)

    fun getAllBolusSince(start: Long): Flow<List<BolusEntity>>

}