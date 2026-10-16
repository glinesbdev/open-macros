package dev.glinesb.openmacros.photo

import dev.glinesb.openmacros.domain.Macros
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/**
 * Pure (JVM-testable) request building and response validation for the xAI chat completions call.
 * Nothing the model returns is trusted: numbers are range-checked, bad items dropped, and an
 * estimate with no usable items is an error.
 */
object PhotoEstimateParser {
    const val MAX_ITEMS = 20
    const val MAX_GRAMS = 3000.0
    const val MAX_KCAL = 5000.0
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val SYSTEM_PROMPT = """
        You are a nutrition estimation assistant for a calorie-tracking app.
        Look at the photo and identify each distinct food or drink that is meant to be eaten.
        For each one, estimate the edible portion weight in grams as served in the photo, and the
        energy (kcal), protein, carbohydrate and fat in grams FOR THAT PORTION (not per 100 g).
        Use common reference values (e.g. USDA) and visible cues (plate size, utensils, packaging) for scale.
        Include cooking fats, sauces and dressings you can see as separate items when they are significant.
        confidence is 0 to 1: how sure you are about the item AND its portion size.
        If the photo contains no food, return an empty items array and explain in notes.
        Reply with JSON only, no prose, no markdown:
        {"items":[{"name":string,"grams":number,"kcal":number,"protein_g":number,"carbs_g":number,"fat_g":number,"confidence":number}],"notes":string}
    """.trimIndent()

    /** Structured-output schema (OpenAI-compatible `response_format: json_schema`). */
    val RESPONSE_SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("items") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("name") { put("type", "string") }
                        for (k in listOf("grams", "kcal", "protein_g", "carbs_g", "fat_g", "confidence")) {
                            putJsonObject(k) { put("type", "number") }
                        }
                    }
                    putJsonArray("required") {
                        listOf("name", "grams", "kcal", "protein_g", "carbs_g", "fat_g", "confidence").forEach { add(it) }
                    }
                    put("additionalProperties", false)
                }
            }
            putJsonObject("notes") { put("type", "string") }
        }
        putJsonArray("required") { add("items"); add("notes") }
        put("additionalProperties", false)
    }

    fun buildRequest(model: String, photo: FoodPhoto, structured: Boolean = true): JsonObject = buildJsonObject {
        put("model", model)
        put("reasoning_effort", "low")
        put("max_completion_tokens", 2000)
        put("messages", buildJsonArray {
            addJsonObject { put("role", "system"); put("content", SYSTEM_PROMPT) }
            addJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "image_url")
                        putJsonObject("image_url") {
                            put("url", "data:${photo.mimeType};base64," + Base64.getEncoder().encodeToString(photo.jpeg))
                            put("detail", "high")
                        }
                    }
                    addJsonObject {
                        put("type", "text")
                        put("text", "Estimate the foods in this photo." +
                            (photo.hint?.takeIf { it.isNotBlank() }?.let { " Context from the user: ${it.take(300)}" } ?: ""))
                    }
                }
            }
        })
        if (structured) putJsonObject("response_format") {
            put("type", "json_schema")
            putJsonObject("json_schema") {
                put("name", "food_photo_estimate")
                put("strict", true)
                put("schema", RESPONSE_SCHEMA)
            }
        }
    }

    /** Parses a chat completion response body into a validated estimate. */
    fun parseCompletion(root: JsonElement): PhotoEstimate {
        val obj = root as? JsonObject ?: throw PhotoEstimateException("Unexpected response from xAI")
        val choice = (obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: throw PhotoEstimateException("xAI returned no answer")
        val message = choice["message"] as? JsonObject ?: throw PhotoEstimateException("xAI returned no answer")
        message.string("refusal")?.let { throw PhotoEstimateException("xAI declined: ${it.take(200)}") }
        val content = message.string("content") ?: throw PhotoEstimateException("xAI returned an empty answer")
        if (choice.string("finish_reason") == "length") throw PhotoEstimateException("The answer was cut off. Try again.")
        val ticks = ((obj["usage"] as? JsonObject)?.number("cost_in_usd_ticks"))
        return parseEstimateJson(content).copy(costUsd = ticks?.let { it / 1e10 })
    }

    /** Validates the model's JSON text (tolerating code fences or stray prose around the object). */
    fun parseEstimateJson(text: String): PhotoEstimate {
        val body = extractJsonObject(text) ?: throw PhotoEstimateException("Couldn't read the estimate (no JSON)")
        val obj = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: throw PhotoEstimateException("Couldn't read the estimate (invalid JSON)")
        val notes = obj.string("notes")?.take(500)
        val rawItems = obj["items"] as? JsonArray ?: throw PhotoEstimateException("Couldn't read the estimate (no items)")
        val items = rawItems.mapNotNull { (it as? JsonObject)?.let(::parseItem) }.take(MAX_ITEMS)
        if (items.isEmpty()) {
            throw PhotoEstimateException(notes?.let { "No food recognized: $it" } ?: "No food recognized in the photo")
        }
        return PhotoEstimate(items, notes)
    }

    private fun parseItem(o: JsonObject): EstimatedFood? {
        val name = o.string("name")?.take(80) ?: return null
        val grams = o.number("grams", "estimated_grams", "weight_g")?.takeIf { it > 0 && it <= MAX_GRAMS }
        val protein = o.number("protein_g", "protein").nonNegative() ?: 0.0
        val carbs = o.number("carbs_g", "carbs", "carbohydrates_g").nonNegative() ?: 0.0
        val fat = o.number("fat_g", "fat").nonNegative() ?: 0.0
        val kcal = o.number("kcal", "calories").nonNegative() ?: Macros.kcalFrom(protein, carbs, fat)
        if (kcal > MAX_KCAL || protein > MAX_GRAMS || carbs > MAX_GRAMS || fat > MAX_GRAMS) return null
        if (kcal == 0.0 && protein == 0.0 && carbs == 0.0 && fat == 0.0 && grams == null) return null
        var conf = o.number("confidence") ?: 0.5
        if (conf > 1.0 && conf <= 100.0) conf /= 100.0 // tolerate percentages
        return EstimatedFood(name, grams, Macros(kcal, protein, carbs, fat), conf.coerceIn(0.0, 1.0).toFloat())
    }

    /** First balanced {...} in the text, ignoring braces inside strings. */
    fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{').takeIf { it >= 0 } ?: return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(start, i + 1)
            }
        }
        return null
    }

    /** Human-readable message from an xAI error body ({"error": "..."} or {"error": {"message": "..."}}). */
    fun errorMessage(body: String): String? = try {
        val o = json.parseToJsonElement(body) as? JsonObject
        val e = o?.get("error")
        when (e) {
            is JsonPrimitive -> e.contentOrNull
            is JsonObject -> e.string("message")
            else -> o?.string("message")
        }?.take(200)
    } catch (e: Exception) {
        null
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.number(vararg keys: String): Double? = keys.firstNotNullOfOrNull { k ->
        (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun Double?.nonNegative(): Double? = this?.takeIf { it >= 0 }
}
