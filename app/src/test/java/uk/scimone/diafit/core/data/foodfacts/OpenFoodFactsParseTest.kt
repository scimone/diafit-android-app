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

class UsdaParseTest {
    private fun food(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `matches the zero padded barcode and reads per 100 g values`() {
        val p = parseFdcFood("0016000275287", food("""{"description":"CHEERIOS CEREAL","brandOwner":"General Mills","gtinUpc":"00016000275287",
            "servingSize":20.0,"servingSizeUnit":"GRM","foodNutrients":[{"nutrientId":1005,"value":74.4},{"nutrientId":1003,"value":12.8},
            {"nutrientId":1004,"value":6.41},{"nutrientId":1008,"value":359},{"nutrientId":1079,"value":10.3},{"nutrientId":2000,"value":5.13}]}"""))!!
        assertEquals("Cheerios cereal", p.name)
        assertEquals(74.4, p.per100.carbsG, 0.001)
        assertEquals(20.0, p.servingG!!, 0.001)
        assertEquals("USDA FoodData Central", p.source)
        assertEquals(14.88, p.toComponent().carbsG, 0.001)
    }

    @Test
    fun `other barcodes and foods without carbs are ignored`() {
        val nutrients = """"foodNutrients":[{"nutrientId":1005,"value":10}]"""
        assertNull(parseFdcFood("4000000000000", food("""{"description":"X","gtinUpc":"00016000275287",$nutrients}""")))
        assertNull(parseFdcFood("16000275287", food("""{"description":"X","gtinUpc":"00016000275287","foodNutrients":[]}""")))
    }
}
