package uk.scimone.diafit.core.domain.repository

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.domain.model.RecipeEntity

interface RecipeRepository {
    fun observeAll(): Flow<List<RecipeEntity>>
    suspend fun get(id: Int): RecipeEntity?
    /** Inserts (id 0) or updates; returns the id. */
    suspend fun save(recipe: RecipeEntity): Int
    suspend fun markUsed(id: Int)
    suspend fun delete(id: Int)
}
