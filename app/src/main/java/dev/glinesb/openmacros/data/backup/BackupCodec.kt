package dev.glinesb.openmacros.data.backup

import dev.glinesb.openmacros.data.AppSettings
import dev.glinesb.openmacros.data.db.FoodEntity
import dev.glinesb.openmacros.data.db.LogEntryEntity
import dev.glinesb.openmacros.data.db.WeightEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Full JSON backup. API keys are never included. */
@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long,
    val settings: AppSettings? = null,
    val foods: List<FoodEntity> = emptyList(),
    val entries: List<LogEntryEntity> = emptyList(),
    val weights: List<WeightEntity> = emptyList(),
) {
    companion object { const val FORMAT = "open-macros-backup" }
}

/** Pure (JVM-testable) JSON and CSV encoding. */
object BackupCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun toJson(b: Backup): String = json.encodeToString(Backup.serializer(), b)

    fun fromJson(text: String): Backup {
        val b = json.decodeFromString(Backup.serializer(), text)
        require(b.format == Backup.FORMAT) { "Not an Open Macros backup" }
        return b
    }

    val CSV_HEADER = listOf(
        "type", "date", "meal", "name", "brand", "amount", "unit", "servings",
        "kcal", "protein_g", "carbs_g", "fat_g", "weight_kg",
    )

    /** One flat CSV: `food` rows for the diary and `weight` rows for weigh-ins. Spreadsheet friendly. */
    fun toCsv(entries: List<LogEntryEntity>, weights: List<WeightEntity>): String = buildString {
        appendLine(CSV_HEADER.joinToString(","))
        for (e in entries) {
            appendLine(
                listOf(
                    "food", LocalDate.ofEpochDay(e.epochDay).toString(), e.meal, e.name, e.brand.orEmpty(),
                    num(e.amount), e.unit, num(e.servings), num(e.kcal), num(e.protein), num(e.carbs), num(e.fat), "",
                ).joinToString(",") { escape(it) },
            )
        }
        for (w in weights) {
            appendLine(
                listOf("weight", LocalDate.ofEpochDay(w.epochDay).toString(), "", "", "", "", "", "", "", "", "", "", num(w.weightKg))
                    .joinToString(",") { escape(it) },
            )
        }
    }

    fun fromCsv(text: String): Pair<List<LogEntryEntity>, List<WeightEntity>> {
        val rows = parseCsv(text)
        require(rows.isNotEmpty()) { "Empty CSV" }
        val header = rows.first().map { it.trim().lowercase() }
        fun idx(name: String) = header.indexOf(name).also { require(it >= 0) { "CSV is missing the \"$name\" column" } }
        val iType = idx("type"); val iDate = idx("date")
        val entries = ArrayList<LogEntryEntity>()
        val weights = ArrayList<WeightEntity>()
        var order = 0L
        for (r in rows.drop(1)) {
            if (r.all { it.isBlank() }) continue
            fun col(name: String) = header.indexOf(name).takeIf { it >= 0 && it < r.size }?.let { r[it].trim() }.orEmpty()
            fun d(name: String) = col(name).toDoubleOrNull() ?: 0.0
            val day = LocalDate.parse(r[iDate].trim()).toEpochDay()
            when (r[iType].trim().lowercase()) {
                "food" -> entries += LogEntryEntity(
                    epochDay = day, meal = col("meal").ifEmpty { "SNACKS" }, name = col("name").ifEmpty { "Imported" },
                    brand = col("brand").ifEmpty { null }, amount = d("amount"), unit = col("unit").ifEmpty { "SERVING" },
                    servings = d("servings"), kcal = d("kcal"), protein = d("protein_g"), carbs = d("carbs_g"), fat = d("fat_g"),
                    createdAt = order++,
                )
                "weight" -> col("weight_kg").toDoubleOrNull()?.let { weights += WeightEntity(epochDay = day, weightKg = it, createdAt = order++) }
            }
        }
        return entries to weights.distinctBy { it.epochDay }
    }

    private fun num(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else "%.3f".format(java.util.Locale.ROOT, d).trimEnd('0').trimEnd('.')

    fun escape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** RFC 4180-style parser: quoted fields, doubled quotes, embedded commas/newlines, CRLF or LF. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row += field.toString(); field.clear() }
                '\r' -> {}
                '\n' -> { row += field.toString(); field.clear(); rows += row; row = ArrayList() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }
}
