package uk.scimone.diafit.recipes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.scimone.diafit.core.domain.model.MealComponent
import uk.scimone.diafit.core.domain.model.RecipeEntity
import uk.scimone.diafit.core.domain.model.recipeIdOrNull

class RecipeTest {
    private val rice = MealComponent(name = "Rice", weightG = 200.0, calories = 700.0, carbsG = 156.0, proteinG = 14.0, fatG = 2.0)
    private val oil = MealComponent(name = "Oil", weightG = 20.0, calories = 180.0, carbsG = 0.0, proteinG = 0.0, fatG = 20.0)

    private fun recipe(finished: Double? = null, servings: Int? = null) =
        RecipeEntity(id = 7, name = "Pilaf", ingredients = listOf(rice, oil), finishedWeightG = finished, servings = servings, createdUtc = 0, updatedUtc = 0)

    @Test fun per100UsesRawWeightWithoutFinishedWeight() {
        val p = recipe().per100!!
        assertEquals(220.0, recipe().effectiveWeightG, 0.001)
        assertEquals(156.0 / 220 * 100, p.carbsG, 0.001)
        assertEquals(880.0 / 220 * 100, p.kcal, 0.001)
    }

    @Test fun finishedWeightConcentratesOrDilutesNutrients() {
        // Water cooked in: the same carbs spread over more grams.
        assertEquals(156.0 / 440 * 100, recipe(finished = 440.0).per100!!.carbsG, 0.001)
        // A zero or blank finished weight falls back to the raw weight.
        assertEquals(220.0, recipe(finished = 0.0).effectiveWeightG, 0.001)
    }

    @Test fun portionWeightAndProductForTheMealFlow() {
        val r = recipe(finished = 400.0, servings = 4)
        assertEquals(100.0, r.servingG!!, 0.001)
        val product = r.toProduct()!!
        assertEquals("recipe:7", product.barcode)
        assertEquals(7, product.barcode.recipeIdOrNull())
        // Eating 150 g gives the carbs of 150/400 of the dish.
        assertEquals(156.0 * 150 / 400, product.toComponent(150.0).carbsG, 0.001)
    }

    @Test fun noWeightMeansNoProduct() {
        val empty = RecipeEntity(name = "x", ingredients = emptyList(), finishedWeightG = null, servings = null, createdUtc = 0, updatedUtc = 0)
        assertNull(empty.per100)
        assertNull(empty.toProduct())
        assertTrue("barcode".recipeIdOrNull() == null)
    }
}
