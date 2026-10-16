package dev.glinesb.openmacros.training

import dev.glinesb.openmacros.data.TrainingPlanner
import dev.glinesb.openmacros.data.db.RoutineDayEntity
import dev.glinesb.openmacros.data.db.WorkoutSetEntity
import dev.glinesb.openmacros.domain.WeightUnit
import dev.glinesb.openmacros.domain.training.ExerciseLibrary
import dev.glinesb.openmacros.domain.training.ExerciseType
import dev.glinesb.openmacros.domain.training.Muscle
import dev.glinesb.openmacros.domain.training.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseLibraryTest {
    @Test fun seedIsSizedAndConsistent() {
        val seed = ExerciseLibrary.SEED
        assertTrue("size ${seed.size}", seed.size in 80..120)
        assertEquals(seed.size, seed.map { it.slug }.distinct().size)
        assertEquals(seed.size, seed.map { it.name.lowercase() }.distinct().size)
        for (e in seed) {
            assertTrue(e.slug.matches(Regex("[a-z0-9-]+")))
            assertTrue(e.primary.isNotEmpty())
            assertTrue("${e.slug} secondary overlaps primary", e.secondary.none { it in e.primary })
            assertEquals("${e.slug} cardio tagging", e.type == ExerciseType.CARDIO, Muscle.CARDIOVASCULAR in e.primary)
        }
        assertTrue(seed.count { it.type == ExerciseType.COMPOUND } >= 40)
        assertTrue(seed.count { it.type == ExerciseType.ISOLATION } >= 30)
        assertTrue(seed.count { it.type == ExerciseType.CARDIO } >= 8)
    }

    @Test fun seedEntityRoundTripsMuscles() {
        val bench = ExerciseLibrary.bySlug.getValue("barbell-bench-press")
        val e = TrainingPlanner.seedEntity(bench)
        assertEquals(bench.primary, e.primary)
        assertEquals(bench.secondary, e.secondary)
        assertEquals(false, e.isCustom)
    }

    @Test fun moveAndNextDay() {
        assertEquals(listOf("b", "a", "c"), TrainingPlanner.move(listOf("a", "b", "c"), 1, -1))
        assertEquals(listOf("a", "b", "c"), TrainingPlanner.move(listOf("a", "b", "c"), 0, -1))
        val days = (1L..4L).map { RoutineDayEntity(id = it, routineId = 1, name = "D$it", position = it.toInt()) }
        assertEquals(1L, TrainingPlanner.nextDay(days, null)!!.id)
        assertEquals(3L, TrainingPlanner.nextDay(days, 2)!!.id)
        assertEquals(1L, TrainingPlanner.nextDay(days, 4)!!.id) // wraps
        assertEquals(1L, TrainingPlanner.nextDay(days, 99)!!.id) // deleted day: start over
        assertNull(TrainingPlanner.nextDay(emptyList(), 1))
        assertTrue(TrainingPlanner.newKey() < 0 && TrainingPlanner.newKey() != TrainingPlanner.newKey())
    }

    @Test fun prefillCopiesLastSessionAndPadsToTarget() {
        val last = listOf(
            WorkoutSetEntity(id = 1, workoutExerciseId = 9, position = 0, setType = SetType.WARMUP.name, reps = 5, weightKg = 40.0, unit = "KG", done = true),
            WorkoutSetEntity(id = 2, workoutExerciseId = 9, position = 1, reps = 8, weightKg = 60.0, unit = "KG", done = true),
            WorkoutSetEntity(id = 3, workoutExerciseId = 9, position = 2, reps = 7, weightKg = 60.0, unit = "KG", done = true),
        )
        val p = TrainingPlanner.prefill(last, 3, WeightUnit.LB, 42)
        assertEquals(4, p.size)
        assertEquals(listOf(SetType.WARMUP, SetType.WORKING, SetType.WORKING, SetType.WORKING), p.map { it.type })
        assertEquals(listOf(5, 8, 7, 7), p.map { it.reps })
        assertTrue(p.all { !it.done && it.workoutExerciseId == 42L && it.unit == "LB" && it.id == 0L })
        assertEquals(listOf(0, 1, 2, 3), p.map { it.position })
        val fresh = TrainingPlanner.prefill(emptyList(), null, WeightUnit.KG, 1)
        assertEquals(1, fresh.size); assertNull(fresh.single().reps)
        assertEquals(3, TrainingPlanner.prefill(emptyList(), 3, WeightUnit.KG, 1).size)
    }
}
