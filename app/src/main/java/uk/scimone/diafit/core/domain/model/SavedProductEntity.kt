package uk.scimone.diafit.core.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A scanned product the user saved in a meal, kept so it can be picked again without scanning. */
@Entity(indices = [Index(value = ["lastUsedUtc"])])
data class SavedProductEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String?,
    val kcal100: Double,
    val carbs100: Double,
    val sugar100: Double,
    val fiber100: Double,
    val protein100: Double,
    val fat100: Double,
    val servingG: Double?,
    val packageG: Double?,
    val lastUsedUtc: Long,
    val useCount: Int = 1
) {
    fun toProduct() = FoodProduct(
        barcode = barcode, name = name, brand = brand,
        per100 = NutrientsPer100(kcal100, carbs100, sugar100, fiber100, protein100, fat100),
        servingG = servingG, packageG = packageG
    )

    companion object {
        fun of(p: FoodProduct, now: Long, useCount: Int = 1) = SavedProductEntity(
            p.barcode, p.name, p.brand, p.per100.kcal, p.per100.carbsG, p.per100.sugarG, p.per100.fiberG,
            p.per100.proteinG, p.per100.fatG, p.servingG, p.packageG, now, useCount
        )
    }
}

/** What a saved meal's scanned component says about its product, when the original lookup is gone. */
fun MealComponent.toSavedProductOrNull(now: Long): SavedProductEntity? {
    val code = barcode ?: return null
    if (weightG <= 0.0) return null
    val k = 100.0 / weightG
    return SavedProductEntity(code, name, null, calories * k, carbsG * k, sugarG * k, fiberG * k, proteinG * k, fatG * k, servingG, packageG, now)
}
