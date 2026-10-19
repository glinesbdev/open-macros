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
    private val training: dev.glinesb.openmacros.data.TrainingRepository,
) {
    suspend fun exportJson(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = Backup(
            exportedAt = System.currentTimeMillis(),
            settings = settings.state.value,
            foods = db.foods().all(),
            entries = db.log().all(),
            weights = db.weights().all(),
            training = TrainingBackup(
                exercises = db.exercises().all(),
                routines = db.routines().routines(),
                routineDays = db.routines().allDays(),
                routineExercises = db.routines().allExercises(),
                workouts = db.workouts().allWorkouts(),
                workoutExercises = db.workouts().allExercises(),
                workoutSets = db.workouts().allSets(),
            ),
        )
        write(uri, BackupCodec.toJson(backup))
    }

    /** All sets from finished workouts as CSV. */
    suspend fun exportTrainingCsv(uri: Uri) = withContext(Dispatchers.IO) {
        write(uri, BackupCodec.setsToCsv(db.workouts().allFinishedSets()))
    }

    suspend fun exportCsv(uri: Uri) = withContext(Dispatchers.IO) {
        write(uri, BackupCodec.toCsv(db.log().all(), db.weights().all()))
    }

    /**
     * Replaces the diary and weights (and, for JSON, foods and settings, plus training data when the
     * backup has it) with the file's contents.
     */
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
                b.training?.let { t ->
                    // Parents before children; deleting workouts/routines cascades to their rows.
                    db.workouts().deleteAll(); db.routines().deleteAll(); db.exercises().deleteAll()
                    db.exercises().insertAll(t.exercises)
                    db.routines().insertAllRoutines(t.routines)
                    db.routines().insertAllDays(t.routineDays)
                    db.routines().insertAllExercises(t.routineExercises)
                    db.workouts().insertAllWorkouts(t.workouts)
                    db.workouts().insertAllExercises(t.workoutExercises)
                    db.workouts().insertAllSets(t.workoutSets)
                    // Backups from before bodyweight sets: flag bodyweight exercises, 0-weight sets become BW.
                    if (b.version < 3) db.openHelper.writableDatabase.let { w -> dev.glinesb.openmacros.data.db.Migrations.BODYWEIGHT_FIXUP.forEach(w::execSQL) }
                }
            }
            b.settings?.let { s ->
                // Pre-training backups don't know about training settings; keep the current ones then.
                settings.update { cur -> if (b.training == null) s.copy(trainingUnit = cur.trainingUnit, activeRoutineId = cur.activeRoutineId) else s }
            }
            training.ensureSeeded()
            ImportSummary(b.entries.size, b.weights.size, b.foods.size, b.training?.workouts?.count { it.finishedAt != null } ?: 0)
        } else {
            val (entries, weights) = BackupCodec.fromCsv(trimmed)
            db.withTransaction {
                db.log().deleteAll(); db.weights().deleteAll()
                db.log().insertAll(entries)
                db.weights().insertAll(weights)
            }
            ImportSummary(entries.size, weights.size, 0, 0)
        }
    }

    private fun write(uri: Uri, text: String) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) }
            ?: error("Couldn't write the file")
    }
}

data class ImportSummary(val entries: Int, val weights: Int, val foods: Int, val workouts: Int = 0)

