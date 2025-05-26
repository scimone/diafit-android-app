package uk.scimone.diafit.core.domain.repository

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.CgmEntity

interface CgmRepository {

    fun getLatestCGM(): Flow<CgmEntity>

    suspend fun insertCGMValue(cgmValue: CgmEntity)
}