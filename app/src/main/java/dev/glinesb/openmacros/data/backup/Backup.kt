package dev.glinesb.openmacros.data.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import dev.glinesb.openmacros.data.AppSettings
import dev.glinesb.openmacros.data.SettingsStore
import dev.glinesb.openmacros.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads/writes backups through the Storage Access Framework. Nothing ever leaves the device otherwise. */
class BackupManager(
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val resolver: ContentResolver,
) {
    suspend fun exportJson(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = Backup(
            exportedAt = System.currentTimeMillis(),
            settings = settings.state.value,
            foods = db.foods().all(),
            entries = db.log().all(),
            weights = db.weights().all(),
        )
        write(uri, BackupCodec.toJson(backup))
    }

    suspend fun exportCsv(uri: Uri) = withContext(Dispatchers.IO) {
        write(uri, BackupCodec.toCsv(db.log().all(), db.weights().all()))
    }

    /** Replaces the diary and weights (and, for JSON, foods and settings) with the file's contents. */
    suspend fun import(uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Couldn't open the file")
        val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (trimmed.startsWith("{")) {
            val b = BackupCodec.fromJson(trimmed)
            db.withTransaction {
                db.log().deleteAll(); db.weights().deleteAll(); db.foods().deleteAll()
                db.foods().insertAll(b.foods)
                db.log().insertAll(b.entries)
                db.weights().insertAll(b.weights)
            }
            b.settings?.let { s -> settings.update { s } }
            ImportSummary(b.entries.size, b.weights.size, b.foods.size)
        } else {
            val (entries, weights) = BackupCodec.fromCsv(trimmed)
            db.withTransaction {
                db.log().deleteAll(); db.weights().deleteAll()
                db.log().insertAll(entries)
                db.weights().insertAll(weights)
            }
            ImportSummary(entries.size, weights.size, 0)
        }
    }

    private fun write(uri: Uri, text: String) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) }
            ?: error("Couldn't write the file")
    }
}

data class ImportSummary(val entries: Int, val weights: Int, val foods: Int)

