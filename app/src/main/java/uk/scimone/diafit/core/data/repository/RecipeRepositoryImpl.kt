package uk.scimone.diafit.core.data.repository

import kotlinx.coroutines.flow.Flow
import uk.scimone.diafit.core.data.local.RecipeDao
import uk.scimone.diafit.core.domain.model.RecipeEntity
import uk.scimone.diafit.core.domain.repository.RecipeRepository

class RecipeRepositoryImpl(private val dao: RecipeDao) : RecipeRepository {
    override fun observeAll(): Flow<List<RecipeEntity>> = dao.observeAll()
    override suspend fun get(id: Int) = dao.get(id)
    override suspend fun save(recipe: RecipeEntity): Int =
        dao.upsert(recipe).toInt().let { if (recipe.id != 0) recipe.id else it }
    override suspend fun markUsed(id: Int) = dao.markUsed(id, System.currentTimeMillis())
    override suspend fun delete(id: Int) = dao.delete(id)
}
