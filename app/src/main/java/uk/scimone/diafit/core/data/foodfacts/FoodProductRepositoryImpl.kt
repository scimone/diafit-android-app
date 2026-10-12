package uk.scimone.diafit.core.data.foodfacts

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import uk.scimone.diafit.core.domain.model.FoodProduct
import uk.scimone.diafit.core.domain.model.ProductLookup
import uk.scimone.diafit.core.domain.repository.FoodProductRepository

/** Open Food Facts first; when it has nothing (or can't be reached), USDA FoodData Central (if the user set a key). */
class FoodProductRepositoryImpl(
    private val openFoodFacts: OpenFoodFactsApi,
    private val usda: UsdaFoodDataApi
) : FoodProductRepository {

    override suspend fun lookup(barcode: String): ProductLookup {
        val first = openFoodFacts.lookup(barcode)
        if (first is ProductLookup.Found) return first
        return usda.lookup(barcode).takeIf { it is ProductLookup.Found } ?: first
    }

    private val searchCache = object : LinkedHashMap<String, List<FoodProduct>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<FoodProduct>>) = size > 30
    }

    override suspend fun searchByName(query: String): Result<List<FoodProduct>> {
        val key = query.trim().lowercase()
        synchronized(searchCache) { searchCache[key] }?.let { return Result.success(it) }
        return searchRemote(query).also { r -> r.getOrNull()?.takeIf { it.isNotEmpty() }?.let { synchronized(searchCache) { searchCache[key] = it } } }
    }

    private suspend fun searchRemote(query: String): Result<List<FoodProduct>> = coroutineScope {
        val off = async { openFoodFacts.search(query) }
        val us = async { usda.search(query) }
        val a = off.await()
        val b = us.await()
        if (a.isFailure && b.isFailure) return@coroutineScope Result.failure(a.exceptionOrNull() ?: IllegalStateException("Search failed"))
        // Interleave so both databases show their best matches near the top.
        val x = a.getOrDefault(emptyList())
        val y = b.getOrDefault(emptyList())
        Result.success((0 until maxOf(x.size, y.size)).flatMap { i -> listOfNotNull(x.getOrNull(i), y.getOrNull(i)) })
    }

    override suspend fun testUsdaKey(apiKey: String) = usda.testKey(apiKey)
}
