package dev.glinesb.openmacros.training

import dev.glinesb.openmacros.data.backup.Backup
import dev.glinesb.openmacros.data.backup.BackupCodec
import dev.glinesb.openmacros.data.backup.TrainingBackup
import dev.glinesb.openmacros.data.db.ExerciseEntity
import dev.glinesb.openmacros.data.db.RoutineDayEntity
import dev.glinesb.openmacros.data.db.RoutineEntity
import dev.glinesb.openmacros.data.db.RoutineExerciseEntity
import dev.glinesb.openmacros.data.db.SetRow
import dev.glinesb.openmacros.data.db.WorkoutEntity
import dev.glinesb.openmacros.data.db.WorkoutExerciseEntity
import dev.glinesb.openmacros.data.db.WorkoutSetEntity
import dev.glinesb.openmacros.data.AppSettings
import dev.glinesb.openmacros.domain.Units
import dev.glinesb.openmacros.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingBackupTest {
    private val training = TrainingBackup(
        exercises = listOf(ExerciseEntity(1, "custom-x", "My Press", "CHEST", "TRICEPS", "MACHINE", "COMPOUND", true)),
        routines = listOf(RoutineEntity(1, "UL", "", 0, 5)),
        routineDays = listOf(RoutineDayEntity(1, 1, "Upper A", 0)),
        routineExercises = listOf(RoutineExerciseEntity(1, 1, 1, 0, 3, 8, 12, 90, "pause")),
        workouts = listOf(WorkoutEntity(1, "Upper A", 20000, 10, 20, 1, 1, "felt good")),
        workoutExercises = listOf(WorkoutExerciseEntity(1, 1, 1, "My Press", "COMPOUND", 0, "LB", 8, 12, 90, "")),
        workoutSets = listOf(WorkoutSetEntity(1, 1, 0, "WORKING", 10, Units.lbToKg(135.0), "LB", true)),
    )

    @Test fun trainingRoundTripsInJson() {
        val b = Backup(exportedAt = 1, settings = AppSettings(trainingUnit = WeightUnit.KG, activeRoutineId = 1), training = training)
        val back = BackupCodec.fromJson(BackupCodec.toJson(b))
        assertEquals(b, back)
        assertEquals(WeightUnit.KG, back.settings!!.trainingUnit)
    }

    @Test fun olderBackupsWithoutTrainingStillImport() {
        val v1 = """{"format":"open-macros-backup","version":1,"exportedAt":1,
            "settings":{"weightUnit":"KG"},"foods":[],"entries":[],"weights":[]}"""
        val b = BackupCodec.fromJson(v1)
        assertNull(b.training) // import leaves existing training data alone
        assertEquals(WeightUnit.LB, b.settings!!.trainingUnit)
        assertNull(b.settings!!.activeRoutineId)
    }

    @Test fun setsCsv() {
        val rows = listOf(
            SetRow(1, "Upper, A", 20000, 0, 1, 1, "Bench", "COMPOUND", 0, false, 1, 0, "WARMUP", 5, 60.0, "KG", true),
            SetRow(1, "Upper, A", 20000, 0, 1, 1, "Bench", "COMPOUND", 0, false, 2, 1, "WORKING", 5, Units.lbToKg(225.0), "LB", true),
            SetRow(1, "Upper, A", 20000, 0, 2, 2, "Rowing Machine", "CARDIO", 1, false, 3, 0, "WORKING", 20, null, "LB", true),
        )
        val lines = BackupCodec.setsToCsv(rows).trim().lines()
        assertEquals("date,workout,exercise,set,set_type,reps,weight,unit,weight_kg,e1rm_kg,bodyweight", lines[0])
        assertEquals("2024-10-04,\"Upper, A\",Bench,1,warmup,5,60,kg,60,,", lines[1])
        val f = BackupCodec.parseCsv(lines[2] + "\n").single()
        assertEquals("225", f[6]); assertEquals("lb", f[7])
        assertEquals(102.058, f[8].toDouble(), 1e-3)
        assertEquals(119.07, f[9].toDouble(), 1e-2) // Epley on kg
        assertEquals("2024-10-04,\"Upper, A\",Rowing Machine,1,working,20,,,,,", lines[3])
        val bw = BackupCodec.setsToCsv(listOf(SetRow(1, "Pull", 20000, 0, 3, 3, "Pull-Up", "COMPOUND", 0, true, 4, 0, "WORKING", 8,
            Units.lbToKg(25.0), "LB", true))).trim().lines()[1]
        assertEquals("2024-10-04,Pull,Pull-Up,1,working,8,25,lb,11.34,,yes", bw.replace(Regex("11\\.3\\d+"), "11.34"))
    }
}
