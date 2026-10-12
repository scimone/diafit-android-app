package uk.scimone.diafit.core.data.foodfacts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OpenFoodFactsParseTest {
    private fun parse(json: String) = parseProduct("123", Json.parseToJsonElement(json).jsonObject)

    @Test
    fun `maps per 100 g values and the printed serving`() {
        val p = parse("""{"product_name":"Hazelnut spread","brands":"Nutella, Ferrero","serving_quantity":"15",
            "product_quantity":"400","nutriments":{"carbohydrates_100g":57.5,"sugars_100g":56.3,"fiber_100g":0,
            "proteins_100g":6.3,"fat_100g":30.9,"energy-kcal_100g":539}}""")!!
        assertEquals("Hazelnut spread", p.name)
        assertEquals("Nutella", p.brand)
        assertEquals(15.0, p.servingG!!, 0.001)
        assertEquals(400.0, p.packageG!!, 0.001)
        val c = p.toComponent()
        assertEquals(15.0, c.weightG, 0.001)
        assertEquals(8.625, c.carbsG, 0.001)
        assertEquals("123", c.barcode)
        // Doubling the portion doubles the nutrients.
        assertEquals(17.25, c.withWeight(30.0).carbsG, 0.001)
    }

    @Test
    fun `defaults to 100 g without a serving and derives energy from kJ or macros`() {
        val p = parse("""{"product_name":"Juice","nutriments":{"carbohydrates_100g":"10,5","energy_100g":184}}""")!!
        assertNull(p.servingG)
        assertEquals(100.0, p.defaultPortionG, 0.001)
        assertEquals(10.5, p.per100.carbsG, 0.001)
        assertEquals(44.0, p.per100.kcal, 0.5)
    }

    @Test
    fun `products without carbohydrate data are not usable`() {
        assertNull(parse("""{"product_name":"Mystery","nutriments":{"proteins_100g":3}}"""))
        assertNotNull(parse("""{"nutriments":{"carbohydrates_100g":0}}"""))
    }
}
