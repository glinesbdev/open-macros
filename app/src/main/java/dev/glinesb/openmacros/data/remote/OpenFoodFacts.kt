package dev.glinesb.openmacros.data.remote

import dev.glinesb.openmacros.domain.FoodItem
import dev.glinesb.openmacros.domain.FoodSource
import dev.glinesb.openmacros.domain.Macros
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * Open Food Facts (https://world.openfoodfacts.org). No key needed; requests carry [Http.USER_AGENT].
 * Data is © Open Food Facts contributors under the Open Database License (ODbL 1.0).
 * Search is rate-limited by OFF (about 10 requests/minute), so the app only searches on submit.
 */
class OpenFoodFactsApi(
    private val baseUrl: String = "https://world.openfoodfacts.org",
    private val searchUrl: String = "https://search.openfoodfacts.org",
) {
    private val fields = "code,product_name,product_name_en,generic_name,brands,nutriments,serving_size,serving_quantity"

    /**
     * Full-text search. Uses OFF's newer search service (search.openfoodfacts.org, "search-a-licious")
     * and falls back to the legacy /cgi/search.pl endpoint if that fails.
     */
    suspend fun search(query: String, pageSize: Int = 20): List<FoodItem> {
        val q = URLEncoder.encode(query, "UTF-8")
        return try {
            OpenFoodFactsParser.parseSearch(Http.getJson("$searchUrl/search?q=$q&page_size=$pageSize&fields=$fields"))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val url = "$baseUrl/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1" +
                "&page_size=$pageSize&fields=$fields"
            OpenFoodFactsParser.parseSearch(Http.getJson(url))
        }
    }

    /** Returns null when the product isn't in the database (or has no usable nutrition data). */
    suspend fun byBarcode(barcode: String): FoodItem? {
        val code = barcode.filter { it.isDigit() }
        if (code.isEmpty()) return null
        val root = try {
            Http.getJson("$baseUrl/api/v2/product/$code.json?fields=$fields")
        } catch (e: HttpStatusException) {
            if (e.code == 404) return null else throw e
        }
        return OpenFoodFactsParser.parseProductResponse(root)
    }
}

object OpenFoodFactsParser {
    /** Handles both the legacy ("products") and search-a-licious ("hits") response shapes. */
    fun parseSearch(root: JsonElement): List<FoodItem> =
        ((root as? JsonObject)?.let { it["hits"] ?: it["products"] } as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::parseProduct) }

    fun parseProductResponse(root: JsonElement): FoodItem? {
        val obj = root as? JsonObject ?: return null
        if (obj.num("status") == 0.0) return null
        val product = obj["product"] as? JsonObject ?: return null
        val code = obj.str("code")
        return parseProduct(if (product.str("code") == null && code != null) JsonObject(product + ("code" to obj["code"]!!)) else product)
    }

    fun parseProduct(p: JsonObject): FoodItem? {
        val code = p.str("code") ?: return null
        val name = p.str("product_name") ?: p.str("product_name_en") ?: p.str("generic_name") ?: return null
        val n = p["nutriments"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
        val kcal100 = n.num("energy-kcal_100g") ?: n.num("energy_100g")?.let { it / 4.184 } ?: return null
        val per100 = Macros(
            kcal = kcal100,
            protein = n.num("proteins_100g") ?: 0.0,
            carbs = n.num("carbohydrates_100g") ?: 0.0,
            fat = n.num("fat_100g") ?: 0.0,
        )
        val servingQty = p.num("serving_quantity")?.takeIf { it > 0 && it < 5000 }
        val servingSize = p.str("serving_size")
        val (label, grams) = if (servingQty != null) (servingSize ?: "${fmt(servingQty)} g") to servingQty else "100 g" to 100.0
        return FoodItem(
            source = FoodSource.OPEN_FOOD_FACTS,
            sourceId = code,
            name = name,
            brand = firstBrand(p["brands"]),
            barcode = code,
            servingLabel = label,
            servingGrams = grams,
            perServing = per100 * (grams / 100.0),
        )
    }

    /** "brands" is a comma-separated string in the product API and an array in search-a-licious. */
    private fun firstBrand(e: JsonElement?): String? = when (e) {
        is JsonArray -> (e.firstOrNull() as? JsonPrimitive)?.contentOrNull
        is JsonPrimitive -> e.contentOrNull?.split(',')?.firstOrNull()
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else "%.1f".format(d)
}
