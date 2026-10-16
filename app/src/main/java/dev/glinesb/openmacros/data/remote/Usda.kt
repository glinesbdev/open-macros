package dev.glinesb.openmacros.data.remote

import dev.glinesb.openmacros.domain.FoodItem
import dev.glinesb.openmacros.domain.FoodSource
import dev.glinesb.openmacros.domain.Macros
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder
import java.util.Locale

/**
 * USDA FoodData Central (https://fdc.nal.usda.gov), public domain (CC0).
 * Needs an api.data.gov key; without the user's own key we fall back to DEMO_KEY, which is shared
 * and heavily rate-limited (roughly 30 requests/hour and 50/day per IP).
 */
class UsdaApi(private val baseUrl: String = "https://api.nal.usda.gov/fdc/v1") {
    suspend fun search(query: String, apiKey: String, pageSize: Int = 20): List<FoodItem> {
        val q = URLEncoder.encode(query, "UTF-8")
        val key = URLEncoder.encode(apiKey, "UTF-8")
        val types = URLEncoder.encode("Foundation,SR Legacy,Survey (FNDDS),Branded", "UTF-8")
        val url = "$baseUrl/foods/search?query=$q&pageSize=$pageSize&dataType=$types&api_key=$key"
        return UsdaParser.parseSearch(Http.getJson(url, redact = apiKey))
    }

    companion object {
        const val DEMO_KEY = "DEMO_KEY"
    }
}

object UsdaParser {
    fun parseSearch(root: JsonElement): List<FoodItem> =
        ((root as? JsonObject)?.get("foods") as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::parseFood) }

    /** Search-result nutrient values are per 100 g (or 100 ml) for every data type. */
    fun parseFood(f: JsonObject): FoodItem? {
        val id = f.num("fdcId")?.toLong()?.toString() ?: return null
        val description = f.str("description") ?: return null
        val nutrients = (f["foodNutrients"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

        fun find(id: Int, number: String, unit: String? = null): Double? = nutrients.firstOrNull { n ->
            (n.num("nutrientId")?.toInt() == id || n.str("nutrientNumber") == number) &&
                (unit == null || n.str("unitName").equals(unit, ignoreCase = true))
        }?.num("value")

        val kcal = find(1008, "208", "KCAL") ?: find(2047, "957") ?: find(2048, "958")
            ?: find(1062, "268", "kJ")?.let { it / 4.184 } ?: return null
        val per100 = Macros(
            kcal = kcal,
            protein = find(1003, "203") ?: 0.0,
            carbs = find(1005, "205") ?: 0.0,
            fat = find(1004, "204") ?: 0.0,
        )
        val unit = f.str("servingSizeUnit")?.lowercase(Locale.US)
        val size = f.num("servingSize")?.takeIf { it > 0 && (unit == "g" || unit == "grm" || unit == "ml" || unit == "mlt") }
        val household = f.str("householdServingFullText")?.let { if (it.any(Char::isLowerCase)) it else it.lowercase(Locale.US) }
        val (label, grams) = if (size != null) {
            val s = if (size % 1.0 == 0.0) size.toLong().toString() else "%.1f".format(size)
            (if (household != null) "$household ($s g)" else "$s g") to size
        } else "100 g" to 100.0
        return FoodItem(
            source = FoodSource.USDA,
            sourceId = id,
            name = prettify(description),
            brand = (f.str("brandName") ?: f.str("brandOwner"))?.let(::prettify),
            barcode = f.str("gtinUpc"),
            servingLabel = label,
            servingGrams = grams,
            perServing = per100 * (grams / 100.0),
        )
    }

    /** Branded USDA entries are often SHOUTING; make them readable. */
    fun prettify(s: String): String {
        if (s.any { it.isLowerCase() }) return s
        return s.lowercase(Locale.US).split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.US) } }
    }
}
