package dev.glinesb.openmacros.photo

import dev.glinesb.openmacros.domain.FoodItem
import dev.glinesb.openmacros.domain.FoodSource
import dev.glinesb.openmacros.domain.Macros
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PhotoEstimateTest {
    private fun completion(content: String, extra: String = ""): String {
        val escaped = Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(content))
        return """{"id":"x","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":$escaped,"refusal":null},"finish_reason":"stop"}]$extra}"""
    }

    private fun expectError(block: () -> Unit): String {
        try { block() } catch (e: PhotoEstimateException) { return e.message.orEmpty() }
        fail("expected PhotoEstimateException"); return ""
    }

    @Test fun parsesStrictJson() {
        val est = PhotoEstimateParser.parseEstimateJson(
            """{"items":[{"name":"Grilled chicken breast","grams":150,"kcal":248,"protein_g":46.5,"carbs_g":0,"fat_g":5.4,"confidence":0.8},
               {"name":"White rice","grams":180,"kcal":234,"protein_g":4.8,"carbs_g":51,"fat_g":0.5,"confidence":0.6}],"notes":"Oil not visible"}""",
        )
        assertEquals(2, est.items.size)
        assertEquals("Grilled chicken breast", est.items[0].name)
        assertEquals(150.0, est.items[0].estimatedGrams!!, 1e-9)
        assertEquals(Macros(248.0, 46.5, 0.0, 5.4), est.items[0].macros)
        assertEquals(0.8f, est.items[0].confidence, 1e-6f)
        assertEquals("Oil not visible", est.notes)
    }

    @Test fun parsesCompletionEnvelopeWithFencesAndCost() {
        val content = "```json\n{\"items\":[{\"name\":\"Apple\",\"grams\":180,\"kcal\":94,\"protein_g\":0.5,\"carbs_g\":25,\"fat_g\":0.3,\"confidence\":0.9}],\"notes\":\"\"}\n```"
        val est = PhotoEstimateParser.parseCompletion(Json.parseToJsonElement(completion(content, ""","usage":{"cost_in_usd_ticks":120000000}""")))
        assertEquals("Apple", est.items.single().name)
        assertNull(est.notes)
        assertEquals(0.012, est.costUsd!!, 1e-12)
    }

    @Test fun toleratesAlternateKeysPercentConfidenceAndMissingKcal() {
        val est = PhotoEstimateParser.parseEstimateJson(
            """Here you go: {"items":[{"name":"Eggs","estimated_grams":"100","protein":12,"carbs":1,"fat":10,"confidence":85}]} thanks""",
        )
        val e = est.items.single()
        assertEquals(100.0, e.estimatedGrams!!, 1e-9)
        assertEquals(12 * 4 + 1 * 4 + 10 * 9.0, e.macros.kcal, 1e-9)
        assertEquals(0.85f, e.confidence, 1e-6f)
    }

    @Test fun dropsInvalidItemsAndClamps() {
        val est = PhotoEstimateParser.parseEstimateJson(
            """{"items":[{"name":"","grams":10,"kcal":10,"protein_g":1,"carbs_g":1,"fat_g":0,"confidence":0.5},
               {"name":"Huge","grams":100,"kcal":99999,"protein_g":1,"carbs_g":1,"fat_g":1,"confidence":0.5},
               {"name":"Negative","grams":-5,"kcal":-10,"protein_g":-1,"carbs_g":2,"fat_g":0,"confidence":2.5},
               "not an object",
               {"name":"Toast","grams":30,"kcal":80,"protein_g":3,"carbs_g":15,"fat_g":1,"confidence":-1}],"notes":"ok"}""",
        )
        assertEquals(listOf("Negative", "Toast"), est.items.map { it.name })
        val neg = est.items[0]
        assertNull(neg.estimatedGrams)
        assertEquals(0.0, neg.macros.protein, 1e-9)
        assertEquals(8.0, neg.macros.kcal, 1e-9) // recomputed from the valid macros (2 g carbs)
        assertEquals(0.025f, neg.confidence, 1e-6f) // values above 1 are read as percentages
        assertEquals(0.0f, est.items[1].confidence, 1e-6f)
    }

    @Test fun capsItemCount() {
        val many = (1..30).joinToString(",") { """{"name":"i$it","grams":10,"kcal":10,"protein_g":1,"carbs_g":1,"fat_g":0,"confidence":0.5}""" }
        assertEquals(PhotoEstimateParser.MAX_ITEMS, PhotoEstimateParser.parseEstimateJson("""{"items":[$many],"notes":""}""").items.size)
    }

    @Test fun errorsAreReported() {
        assertTrue(expectError { PhotoEstimateParser.parseEstimateJson("no json here") }.contains("no JSON"))
        assertTrue(expectError { PhotoEstimateParser.parseEstimateJson("{\"items\": [}") }.contains("invalid JSON"))
        assertTrue(expectError { PhotoEstimateParser.parseEstimateJson("{\"notes\":\"x\"}") }.contains("no items"))
        assertTrue(expectError { PhotoEstimateParser.parseEstimateJson("{\"items\":[],\"notes\":\"That's a cat\"}") }.contains("That's a cat"))
        assertTrue(expectError { PhotoEstimateParser.parseCompletion(Json.parseToJsonElement("{\"choices\":[]}")) }.contains("no answer"))
        val refusal = """{"choices":[{"message":{"role":"assistant","content":null,"refusal":"Can't help"},"finish_reason":"stop"}]}"""
        assertTrue(expectError { PhotoEstimateParser.parseCompletion(Json.parseToJsonElement(refusal)) }.contains("declined"))
        val cut = """{"choices":[{"message":{"role":"assistant","content":"{\"items\":["},"finish_reason":"length"}]}"""
        assertTrue(expectError { PhotoEstimateParser.parseCompletion(Json.parseToJsonElement(cut)) }.contains("cut off"))
    }

    @Test fun extractJsonIgnoresBracesInStrings() {
        assertEquals("""{"a":"}{","b":{"c":1}}""", PhotoEstimateParser.extractJsonObject("""x {"a":"}{","b":{"c":1}} y"""))
        assertNull(PhotoEstimateParser.extractJsonObject("""{"a":1"""))
    }

    @Test fun errorMessageShapes() {
        assertEquals("Incorrect API key", PhotoEstimateParser.errorMessage("""{"code":"x","error":"Incorrect API key"}"""))
        assertEquals("bad", PhotoEstimateParser.errorMessage("""{"error":{"message":"bad"}}"""))
        assertNull(PhotoEstimateParser.errorMessage("<html>"))
    }

    @Test fun requestShape() {
        val req = PhotoEstimateParser.buildRequest("grok-4.7", FoodPhoto(byteArrayOf(1, 2, 3), hint = "fried"))
        assertEquals("grok-4.7", req["model"]!!.jsonPrimitive.content)
        val user = (req["messages"] as JsonArray)[1] as JsonObject
        val image = ((user["content"] as JsonArray)[0] as JsonObject)["image_url"] as JsonObject
        assertEquals("data:image/jpeg;base64,AQID", image["url"]!!.jsonPrimitive.content)
        val text = ((user["content"] as JsonArray)[1] as JsonObject)["text"]!!.jsonPrimitive.content
        assertTrue(text.contains("fried"))
        assertTrue("response_format" in req)
        assertFalse("response_format" in PhotoEstimateParser.buildRequest("m", FoodPhoto(byteArrayOf()), structured = false))
    }

    // --- scaling ---

    @Test fun gramsScaleMacrosProportionally() {
        val item = ReviewItem(name = "Rice", grams = 200.0, macros = Macros(260.0, 5.0, 56.0, 0.6), confidence = 0.6f)
        val half = item.withGrams(100.0)
        assertEquals(100.0, half.grams!!, 1e-9)
        assertEquals(Macros(130.0, 2.5, 28.0, 0.3).kcal, half.macros.kcal, 1e-9)
        assertEquals(28.0, half.macros.carbs, 1e-9)
        assertEquals(0.3, half.macros.fat, 1e-9)
        // Round trip back
        assertEquals(260.0, half.withGrams(200.0).macros.kcal, 1e-9)
    }

    @Test fun scalingFromEditedMacros() {
        val item = ReviewItem(name = "Soup", grams = 300.0, macros = Macros(150.0, 6.0, 20.0, 4.0), confidence = 0.4f)
        val edited = item.withMacros(item.macros.copy(kcal = 300.0))
        assertEquals(200.0, edited.withGrams(200.0).macros.kcal, 1e-9)
    }

    @Test fun scalingEdgeCases() {
        val noGrams = ReviewItem(name = "Sauce", grams = null, macros = Macros(50.0, 0.0, 5.0, 3.0), confidence = 0.3f)
        assertEquals(50.0, noGrams.withGrams(30.0).macros.kcal, 1e-9) // no basis to scale from
        assertEquals(30.0, noGrams.withGrams(30.0).grams!!, 1e-9)
        val zero = ReviewItem(name = "x", grams = 100.0, macros = Macros(100.0, 1.0, 1.0, 1.0), confidence = 1f).withGrams(0.0)
        assertEquals(0.0, zero.macros.kcal, 1e-9)
        assertEquals(0.0, zero.withGrams(50.0).macros.kcal, 1e-9) // from 0 g there is nothing to scale
        val same = ReviewItem(name = "x", grams = 100.0, macros = Macros(100.0, 1.0, 1.0, 1.0), confidence = 1f)
        assertEquals(same, same.withGrams(-5.0))
        assertEquals(same, same.withGrams(Double.NaN))
    }

    @Test fun swapKeepsGramsAndUsesDatabaseNutrition() {
        val est = ReviewItem(name = "chicken", grams = 150.0, macros = Macros(300.0, 30.0, 0.0, 15.0), confidence = 0.5f)
        val food = FoodItem(FoodSource.USDA, "171705", "Chicken breast, cooked", null, null, "100 g", 100.0, Macros(165.0, 31.0, 0.0, 3.6))
        val swapped = est.swappedFor(food, foodId = 7)
        assertEquals("Chicken breast, cooked", swapped.name)
        assertEquals(7L, swapped.foodId)
        assertEquals(150.0, swapped.grams!!, 1e-9)
        assertEquals(247.5, swapped.macros.kcal, 1e-9)
        assertNull(swapped.confidence)

        val perServingOnly = FoodItem(FoodSource.CUSTOM, "c", "Bar", "Acme", null, "1 bar", null, Macros(200.0, 10.0, 20.0, 8.0))
        val s2 = est.swappedFor(perServingOnly, 8)
        assertNull(s2.grams)
        assertEquals(200.0, s2.macros.kcal, 1e-9)
    }

    @Test fun confidenceLevels() {
        assertEquals(ConfidenceLevel.HIGH, confidenceLevel(0.9f))
        assertEquals(ConfidenceLevel.MEDIUM, confidenceLevel(0.5f))
        assertEquals(ConfidenceLevel.LOW, confidenceLevel(0.2f))
    }

    @Test fun imageSizingFitsLongEdge() {
        assertEquals(1024 to 768, ImageSizing.fit(4032, 3024))
        assertEquals(768 to 1024, ImageSizing.fit(3024, 4032))
        assertEquals(800 to 600, ImageSizing.fit(800, 600)) // never upscales
        assertEquals(1024 to 1, ImageSizing.fit(10000, 2))
    }
}
