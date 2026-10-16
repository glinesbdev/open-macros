package dev.glinesb.openmacros.data

import dev.glinesb.openmacros.data.remote.OpenFoodFactsParser
import dev.glinesb.openmacros.data.remote.UsdaParser
import dev.glinesb.openmacros.domain.FoodSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsersTest {
    @Test fun offProductWithServing() {
        val json = """
            {"code":"3017620422003","status":1,"product":{"code":"3017620422003","product_name":"Nutella","brands":"Ferrero, Nutella",
             "serving_size":"15 g","serving_quantity":"15",
             "nutriments":{"energy-kcal_100g":539,"proteins_100g":6.3,"carbohydrates_100g":57.5,"fat_100g":30.9}}}
        """.trimIndent()
        val item = OpenFoodFactsParser.parseProductResponse(Json.parseToJsonElement(json))!!
        assertEquals(FoodSource.OPEN_FOOD_FACTS, item.source)
        assertEquals("Nutella", item.name)
        assertEquals("Ferrero", item.brand)
        assertEquals("15 g", item.servingLabel)
        assertEquals(15.0, item.servingGrams!!, 1e-9)
        assertEquals(80.85, item.perServing.kcal, 1e-9)
        assertEquals(4.635, item.perServing.fat, 1e-9)
    }

    @Test fun offFallsBackToKilojoulesAnd100g() {
        val json = """{"code":"1234567890123","product_name":"Thing","nutriments":{"energy_100g":418.4,"proteins_100g":"2,5"}}"""
        val item = OpenFoodFactsParser.parseProduct(Json.parseToJsonElement(json).jsonObject)!!
        assertEquals(100.0, item.perServing.kcal, 1e-9)
        assertEquals(2.5, item.perServing.protein, 1e-9)
        assertEquals("100 g", item.servingLabel)
    }

    @Test fun offSearchALiciousShape() {
        val json = """{"hits":[{"code":"0894700010137","brands":["Chobani"],"product_name":"Nonfat Greek Yogurt",
            "nutriments":{"energy-kcal_100g":52.9,"proteins_100g":9.41,"carbohydrates_100g":3.53,"fat_100g":0}},
            {"code":"1","product_name":"No nutrition"}]}"""
        val items = OpenFoodFactsParser.parseSearch(Json.parseToJsonElement(json))
        assertEquals(1, items.size)
        assertEquals("Chobani", items[0].brand)
        assertEquals(52.9, items[0].perServing.kcal, 1e-9)
    }

    @Test fun offNotFound() {
        assertNull(OpenFoodFactsParser.parseProductResponse(Json.parseToJsonElement("""{"status":0,"status_verbose":"product not found"}""")))
    }

    @Test fun usdaBrandedUsesServingSize() {
        val json = """
            {"fdcId":2345678,"description":"GREEK YOGURT","dataType":"Branded","brandOwner":"FAGE USA DAIRY INDUSTRY",
             "gtinUpc":"689544001010","servingSize":170,"servingSizeUnit":"g","householdServingFullText":"1 container",
             "foodNutrients":[{"nutrientId":1003,"nutrientNumber":"203","unitName":"G","value":10.0},
                               {"nutrientId":1004,"nutrientNumber":"204","unitName":"G","value":5.0},
                               {"nutrientId":1005,"nutrientNumber":"205","unitName":"G","value":4.0},
                               {"nutrientId":1008,"nutrientNumber":"208","unitName":"KCAL","value":97}]}
        """.trimIndent()
        val item = UsdaParser.parseFood(Json.parseToJsonElement(json).jsonObject)!!
        assertEquals("Greek Yogurt", item.name)
        assertEquals("Fage Usa Dairy Industry", item.brand)
        assertEquals("1 container (170 g)", item.servingLabel)
        assertEquals(164.9, item.perServing.kcal, 1e-9)
        assertEquals(17.0, item.perServing.protein, 1e-9)
    }

    @Test fun usdaFoundationUsesAtwaterEnergyPer100g() {
        val json = """
            {"fdcId":171705,"description":"Chicken, broiler, breast, cooked","dataType":"Foundation",
             "foodNutrients":[{"nutrientId":2047,"nutrientNumber":"957","unitName":"KCAL","value":165},
                               {"nutrientId":1003,"nutrientNumber":"203","unitName":"G","value":31}]}
        """.trimIndent()
        val item = UsdaParser.parseFood(Json.parseToJsonElement(json).jsonObject)!!
        assertEquals("Chicken, broiler, breast, cooked", item.name)
        assertEquals(165.0, item.perServing.kcal, 1e-9)
        assertEquals(100.0, item.servingGrams!!, 1e-9)
    }
}
