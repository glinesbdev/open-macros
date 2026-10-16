package dev.glinesb.openmacros.data

import dev.glinesb.openmacros.data.backup.Backup
import dev.glinesb.openmacros.data.backup.BackupCodec
import dev.glinesb.openmacros.data.db.FoodEntity
import dev.glinesb.openmacros.data.db.LogEntryEntity
import dev.glinesb.openmacros.data.db.WeightEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupCodecTest {
    private val entry = LogEntryEntity(
        id = 3, epochDay = 20000, meal = "LUNCH", foodId = 1, name = "Soup, \"tomato\", canned", brand = "Acme",
        amount = 1.5, unit = "SERVING", servings = 1.5, kcal = 150.0, protein = 3.0, carbs = 25.5, fat = 2.25, createdAt = 5,
    )
    private val weight = WeightEntity(id = 1, epochDay = 20000, weightKg = 80.25, createdAt = 1)

    @Test fun jsonRoundTrip() {
        val b = Backup(
            exportedAt = 1L, settings = AppSettings(),
            foods = listOf(FoodEntity(id = 1, source = "CUSTOM", sourceId = "x", name = "Soup", servingLabel = "1 cup",
                kcal = 100.0, protein = 2.0, carbs = 17.0, fat = 1.5)),
            entries = listOf(entry), weights = listOf(weight),
        )
        assertEquals(b, BackupCodec.fromJson(BackupCodec.toJson(b)))
    }

    @Test fun csvRoundTripWithQuotedFields() {
        val csv = BackupCodec.toCsv(listOf(entry), listOf(weight))
        val (entries, weights) = BackupCodec.fromCsv(csv)
        val e = entries.single()
        assertEquals(entry.name, e.name)
        assertEquals(entry.kcal, e.kcal, 1e-9)
        assertEquals(entry.fat, e.fat, 1e-9)
        assertEquals("LUNCH", e.meal)
        assertEquals(20000L, e.epochDay)
        assertEquals(80.25, weights.single().weightKg, 1e-9)
    }

    @Test fun csvParserHandlesCrlfAndEmbeddedNewlines() {
        val rows = BackupCodec.parseCsv("a,b\r\n\"x\ny\",\"q\"\"q\"\r\n")
        assertEquals(listOf(listOf("a", "b"), listOf("x\ny", "q\"q")), rows)
    }
}
