package dev.glinesb.openmacros.domain

/** Merges search results from several sources into one de-duplicated, relevance-ordered list. */
object FoodMerge {
    /** Barcodes differ in padding between sources (UPC-A vs EAN-13 vs GTIN-14): compare digits without leading zeros. */
    fun normalizeBarcode(code: String?): String? =
        code?.filter { it.isDigit() }?.trimStart('0')?.takeIf { it.length >= 6 }

    private fun normalizeText(s: String?) = s.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** 0..4: exact name, name starts with query, all words present, some words present, none. */
    fun relevance(query: String, item: FoodItem): Int {
        val q = normalizeText(query)
        if (q.isEmpty()) return 0
        val name = normalizeText(item.name)
        val haystack = "$name ${normalizeText(item.brand)}"
        val words = q.split(' ').filter { it.isNotEmpty() }
        return when {
            name == q -> 4
            name.startsWith(q) -> 3
            words.all { haystack.contains(it) } -> 2
            words.any { haystack.contains(it) } -> 1
            else -> 0
        }
    }

    /**
     * Interleaves [sources] (keeping each source's own ranking), drops duplicates (same barcode, or
     * same name + brand), then stable-sorts by [relevance]. Earlier sources win ties and duplicates.
     */
    fun merge(query: String, sources: List<List<FoodItem>>): List<FoodItem> {
        val interleaved = ArrayList<FoodItem>()
        val maxLen = sources.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxLen) for (s in sources) if (i < s.size) interleaved += s[i]

        return dedupe(interleaved).withIndex()
            .sortedWith(compareByDescending<IndexedValue<FoodItem>> { relevance(query, it.value) }.thenBy { it.index })
            .map { it.value }
    }

    /** Drops later duplicates (same normalised barcode, or same name + brand), keeping order. */
    fun dedupe(items: List<FoodItem>): List<FoodItem> {
        val seenBarcodes = HashSet<String>()
        val seenNames = HashSet<String>()
        return items.filter { item ->
            val bc = normalizeBarcode(item.barcode)
            val nameKey = normalizeText(item.name) + "|" + normalizeText(item.brand)
            val dup = (bc != null && bc in seenBarcodes) || nameKey in seenNames
            if (!dup) {
                bc?.let { seenBarcodes += it }
                seenNames += nameKey
            }
            !dup
        }
    }
}
