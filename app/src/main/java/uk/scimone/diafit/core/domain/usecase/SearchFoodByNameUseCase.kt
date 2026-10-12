package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.model.FoodProduct
import uk.scimone.diafit.core.domain.repository.FoodProductRepository

/** Searches the food databases by name (Open Food Facts, plus USDA FoodData Central with a key). */
class SearchFoodByNameUseCase(private val repository: FoodProductRepository) {
    suspend operator fun invoke(query: String): Result<List<FoodProduct>> = repository.searchByName(query)
}
