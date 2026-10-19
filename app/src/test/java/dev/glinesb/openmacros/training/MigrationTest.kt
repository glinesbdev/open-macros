package dev.glinesb.openmacros.training

import dev.glinesb.openmacros.data.db.Migrations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the real migration SQL (v1 → v2 → v3) on SQLite (sqlite-jdbc) against a v1 database built from
 * Room's exported schema, then checks that:
 *  - existing food, diary and weight rows survive untouched;
 *  - every table, column (type, NOT NULL, primary key, default), index and foreign key matches the
 *    v2 schema Room generated, i.e. what Room's own post-migration validation compares.
 */
class MigrationTest {
    private val schemaDir = File("schemas/dev.glinesb.openmacros.data.db.AppDatabase")
    private lateinit var db: Connection

    private fun schema(v: Int): JsonObject = Json.parseToJsonElement(File(schemaDir, "$v.json").readText()).jsonObject["database"]!!.jsonObject
    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
    private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }
    private fun <T> query(sql: String, row: (java.sql.ResultSet) -> T): List<T> =
        db.createStatement().use { st -> st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(row(rs)) } } }

    @Before fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec("PRAGMA foreign_keys = ON")
        val v1 = schema(1)
        for (e in v1["entities"]!!.jsonArray.map { it.jsonObject }) {
            val t = e.str("tableName")
            exec(e.str("createSql").replace("\${TABLE_NAME}", t))
            e["indices"]?.jsonArray?.forEach { exec(it.jsonObject.str("createSql").replace("\${TABLE_NAME}", t)) }
        }
        v1["setupQueries"]!!.jsonArray.forEach { exec(it.jsonPrimitive.content) }
        exec("INSERT INTO foods (id, source, sourceId, name, brand, barcode, servingLabel, servingGrams, kcal, protein, carbs, fat, isFavorite, lastUsedAt, useCount) " +
            "VALUES (1, 'CUSTOM', 'x', 'Oats', NULL, '123', '40 g', 40.0, 150.0, 5.0, 27.0, 3.0, 1, 99, 4)")
        exec("INSERT INTO log_entries (id, epochDay, meal, foodId, name, brand, amount, unit, servings, kcal, protein, carbs, fat, isQuickAdd, createdAt) " +
            "VALUES (7, 20000, 'BREAKFAST', 1, 'Oats', NULL, 80.0, 'GRAM', 2.0, 300.0, 10.0, 54.0, 6.0, 0, 5)")
        exec("INSERT INTO weights (id, epochDay, weightKg, createdAt) VALUES (3, 20000, 81.5, 6)")
    }

    @After fun tearDown() { db.close() }

    @Test fun migrationSqlMatchesExportedSchema() {
        val v2 = schema(2)
        val expected = v2["entities"]!!.jsonArray.map { it.jsonObject }.filter { it.str("tableName") !in setOf("foods", "log_entries", "weights") }
            .flatMap { e ->
                val t = e.str("tableName")
                listOf(e.str("createSql").replace("\${TABLE_NAME}", t)) +
                    (e["indices"]?.jsonArray?.map { it.jsonObject.str("createSql").replace("\${TABLE_NAME}", t) } ?: emptyList())
            }
        assertEquals(expected, Migrations.SQL_1_2)
        assertTrue(Migrations.SQL_1_2.none { it.contains("DROP", ignoreCase = true) || it.contains("ALTER", ignoreCase = true) })
    }

    @Test fun migrateKeepsDataAndProducesV2Schema() {
        db.autoCommit = false
        Migrations.SQL_1_2.forEach(::exec)
        db.commit(); db.autoCommit = true

        // Existing data is untouched.
        assertEquals(listOf("Oats|123|4"), query("SELECT name, barcode, useCount FROM foods") { "${it.getString(1)}|${it.getString(2)}|${it.getInt(3)}" })
        assertEquals(listOf(300.0), query("SELECT kcal FROM log_entries WHERE id = 7") { it.getDouble(1) })
        assertEquals(listOf(81.5), query("SELECT weightKg FROM weights") { it.getDouble(1) })

        assertMatchesSchema(2)
    }

    /** Every table's columns (type, NOT NULL, pk, default), indices and foreign keys match Room's exported schema. */
    private fun assertMatchesSchema(version: Int) {
        for (e in schema(version)["entities"]!!.jsonArray.map { it.jsonObject }) {
            val t = e.str("tableName")
            val cols = query("PRAGMA table_info(`$t`)") { rs ->
                listOf(rs.getString("name"), rs.getString("type"), rs.getInt("notnull") == 1, rs.getInt("pk"), rs.getString("dflt_value")).joinToString("|")
            }.sorted()
            val pk = e["primaryKey"]!!.jsonObject["columnNames"]!!.jsonArray.map { it.jsonPrimitive.content }
            val expectedCols = e["fields"]!!.jsonArray.map { it.jsonObject }.map { f ->
                val name = f.str("columnName")
                listOf(name, f.str("affinity"), f["notNull"]!!.jsonPrimitive.boolean, pk.indexOf(name) + 1, f["defaultValue"]?.jsonPrimitive?.content)
                    .joinToString("|")
            }.sorted()
            assertEquals("columns of $t", expectedCols, cols)

            val indexes = query("PRAGMA index_list(`$t`)") { it.getString("name") to (it.getInt("unique") == 1) }
                .filter { !it.first.startsWith("sqlite_autoindex") }
                .map { (name, unique) -> "$name|$unique|" + query("PRAGMA index_info(`$name`)") { it.getString("name") }.joinToString(",") }.sorted()
            val expectedIdx = (e["indices"]?.jsonArray ?: emptyList()).map { it.jsonObject }.map { i ->
                "${i.str("name")}|${i["unique"]!!.jsonPrimitive.booleanOrNull == true}|" + i["columnNames"]!!.jsonArray.joinToString(",") { it.jsonPrimitive.content }
            }.sorted()
            assertEquals("indices of $t", expectedIdx, indexes)

            val fks = query("PRAGMA foreign_key_list(`$t`)") { "${it.getString("table")}|${it.getString("from")}|${it.getString("to")}|${it.getString("on_delete")}" }.sorted()
            val expectedFks = (e["foreignKeys"]?.jsonArray ?: emptyList()).map { it.jsonObject }.map { f ->
                "${f.str("table")}|${f["columns"]!!.jsonArray.single().jsonPrimitive.content}|${f["referencedColumns"]!!.jsonArray.single().jsonPrimitive.content}|${f.str("onDelete")}"
            }.sorted()
            assertEquals("foreign keys of $t", expectedFks, fks)
        }
        val tables = query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table'") { it.getString(1) }.toSet()
        assertEquals(schema(version)["entities"]!!.jsonArray.map { it.jsonObject.str("tableName") }.toSet(), tables)
    }

    private fun insertTrainingRows() {
        exec("INSERT INTO exercises (id, slug, name, primaryMuscles, secondaryMuscles, equipment, type, isCustom) VALUES " +
            "(1, 'pull-up', 'Pull-Up', 'LATS', '', 'BODYWEIGHT', 'COMPOUND', 0), (2, 'barbell-row', 'Barbell Row', 'UPPER_BACK', '', 'BARBELL', 'COMPOUND', 0), " +
            "(3, 'running', 'Running', 'CARDIOVASCULAR', '', 'BODYWEIGHT', 'CARDIO', 0)")
        exec("INSERT INTO workouts (id, name, epochDay, startedAt, finishedAt, routineId, routineDayId, notes) VALUES (1, 'Pull', 20000, 0, 1, NULL, NULL, '')")
        exec("INSERT INTO workout_exercises (id, workoutId, exerciseId, name, type, position, unit, repMin, repMax, restSeconds, notes) VALUES " +
            "(1, 1, 1, 'Pull-Up', 'COMPOUND', 0, 'LB', 6, 10, 120, ''), (2, 1, 2, 'Barbell Row', 'COMPOUND', 1, 'LB', 6, 10, 120, ''), " +
            "(3, 1, 3, 'Running', 'CARDIO', 2, 'LB', 20, 30, 0, '')")
        exec("INSERT INTO workout_sets (id, workoutExerciseId, position, setType, reps, weightKg, unit, done) VALUES " +
            "(1, 1, 0, 'WORKING', 10, 0.0, 'LB', 1), (2, 1, 1, 'WORKING', 8, 11.34, 'LB', 1), (3, 2, 0, 'WORKING', 8, 0.0, 'LB', 1), (4, 3, 0, 'WORKING', 25, 0.0, 'LB', 1)")
    }

    @Test fun migrate2to3FlagsBodyweightAndTurnsZeroWeightIntoBw() {
        Migrations.SQL_1_2.forEach(::exec)
        insertTrainingRows()
        Migrations.SQL_2_3.forEach(::exec)
        assertMatchesSchema(3)
        assertEquals(listOf("1|1", "2|0", "3|0"), query("SELECT id, isBodyweight FROM workout_exercises ORDER BY id") { "${it.getLong(1)}|${it.getInt(2)}" })
        val w = query("SELECT id, weightKg FROM workout_sets ORDER BY id") { rs -> rs.getLong(1) to rs.getDouble(2).takeUnless { rs.wasNull() } }
        assertEquals(listOf(1L to null, 2L to 11.34, 3L to 0.0, 4L to 0.0), w) // only the pull-up's 0-lb set became BW
        assertEquals(listOf(81.5), query("SELECT weightKg FROM weights") { it.getDouble(1) })
        assertTrue(Migrations.SQL_2_3.none { it.contains("DROP", ignoreCase = true) })
    }

    @Test fun migrate1to3MatchesLatestSchema() {
        (Migrations.SQL_1_2 + Migrations.SQL_2_3).forEach(::exec)
        assertMatchesSchema(3)
        assertEquals(listOf(300.0), query("SELECT kcal FROM log_entries WHERE id = 7") { it.getDouble(1) })
    }

    @Test fun newTablesCascadeAndKeepHistoryWhenACustomExerciseIsDeleted() {
        Migrations.SQL_1_2.forEach(::exec)
        exec("INSERT INTO exercises (id, slug, name, primaryMuscles, secondaryMuscles, equipment, type, isCustom) VALUES (1, 'custom-a', 'My Press', 'CHEST', '', 'MACHINE', 'COMPOUND', 1)")
        exec("INSERT INTO routines (id, name, notes, position, createdAt) VALUES (1, 'R', '', 0, 0)")
        exec("INSERT INTO routine_days (id, routineId, name, position) VALUES (1, 1, 'Day 1', 0)")
        exec("INSERT INTO routine_exercises (id, dayId, exerciseId, position, targetSets, repMin, repMax, restSeconds, notes) VALUES (1, 1, 1, 0, 3, 8, 12, 90, '')")
        exec("INSERT INTO workouts (id, name, epochDay, startedAt, finishedAt, routineId, routineDayId, notes) VALUES (1, 'Day 1', 20000, 0, 1, 1, 1, '')")
        exec("INSERT INTO workout_exercises (id, workoutId, exerciseId, name, type, position, unit, repMin, repMax, restSeconds, notes) VALUES (1, 1, 1, 'My Press', 'COMPOUND', 0, 'LB', 8, 12, 90, '')")
        exec("INSERT INTO workout_sets (id, workoutExerciseId, position, setType, reps, weightKg, unit, done) VALUES (1, 1, 0, 'WORKING', 10, 61.23, 'LB', 1)")

        exec("DELETE FROM exercises WHERE id = 1")
        assertEquals(listOf(0), query("SELECT COUNT(*) FROM routine_exercises") { it.getInt(1) })
        assertEquals(listOf<Long?>(null), query("SELECT exerciseId FROM workout_exercises") { rs -> rs.getLong(1).takeUnless { rs.wasNull() } })
        assertEquals(listOf(1), query("SELECT COUNT(*) FROM workout_sets") { it.getInt(1) })

        exec("DELETE FROM routines WHERE id = 1")
        assertEquals(listOf(0), query("SELECT COUNT(*) FROM routine_days") { it.getInt(1) })
        assertEquals(listOf(1), query("SELECT COUNT(*) FROM workouts") { it.getInt(1) }) // history survives routine deletion

        exec("DELETE FROM workouts WHERE id = 1")
        assertEquals(listOf(0), query("SELECT COUNT(*) FROM workout_sets") { it.getInt(1) })
        assertEquals(listOf(1), query("SELECT COUNT(*) FROM foods") { it.getInt(1) })
    }
}
