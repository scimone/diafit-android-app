package uk.scimone.diafit.core.domain.repository

import uk.scimone.diafit.core.domain.model.ProductLookup

interface FoodProductRepository {
    suspend fun lookup(barcode: String): ProductLookup
}
