package uk.scimone.diafit.core.domain.repository

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.CgmEntity

interface CgmRepository {

    fun getLatestCgmValue(): Flow<CgmEntity>

    suspend fun insertCgm(cgm: CgmEntity)
}