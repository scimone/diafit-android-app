package uk.scimone.diafit.core.domain.repository

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.CgmEntity

interface CgmRepository {

    fun getLatestCgm(): Flow<CgmEntity>

    suspend fun insertCgm(cgm: CgmEntity)

    suspend fun insertAll(cgmList: List<CgmEntity>)

}