package uk.scimone.diafit.core.domain.usecase

import uk.scimone.diafit.core.domain.model.ProductLookup
import uk.scimone.diafit.core.domain.repository.FoodProductRepository

/** Finds a packaged food (name, nutrients per 100 g, serving size) from its barcode. */
class LookupBarcodeUseCase(private val repository: FoodProductRepository) {
    suspend operator fun invoke(barcode: String): ProductLookup = repository.lookup(barcode)
}
