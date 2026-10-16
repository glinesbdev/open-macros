package dev.glinesb.openmacros.training

import dev.glinesb.openmacros.domain.Units
import dev.glinesb.openmacros.domain.WeightUnit
import dev.glinesb.openmacros.domain.training.ExerciseType
import dev.glinesb.openmacros.domain.training.LoggedSet
import dev.glinesb.openmacros.domain.training.Progression
import dev.glinesb.openmacros.domain.training.ProgressionAction
import dev.glinesb.openmacros.domain.training.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    private fun lb(v: Double) = Units.fromDisplayWeight(v, WeightUnit.LB)
    private fun sets(weightKg: Double?, vararg reps: Int) = reps.map { LoggedSet(it, weightKg) }

    @Test fun compoundAllAtTopAddsFivePounds() {
        val s = Progression.suggest(sets(lb(135.0), 12, 12, 12), 8, 12, ExerciseType.COMPOUND, WeightUnit.LB)!!
        assertEquals(ProgressionAction.INCREASE_WEIGHT, s.action)
        assertEquals(140.0, Units.toDisplayWeight(s.weightKg!!, WeightUnit.LB), 1e-9)
        assertEquals(8, s.targetReps) // back to the bottom of the range
        assertTrue(s.message.contains("140 lb"))
    }

    @Test fun compoundInKgAddsTwoAndAHalf() {
        val s = Progression.suggest(sets(100.0, 5, 5, 6), 3, 5, ExerciseType.COMPOUND, WeightUnit.KG)!!
        assertEquals(102.5, s.weightKg!!, 1e-9)
    }

    @Test fun isolationUsesSmallerJumps() {
        val lbS = Progression.suggest(sets(lb(25.0), 15, 15), 10, 15, ExerciseType.ISOLATION, WeightUnit.LB)!!
        assertEquals(27.5, Units.toDisplayWeight(lbS.weightKg!!, WeightUnit.LB), 1e-9)
        val kgS = Progression.suggest(sets(12.0, 15, 16), 10, 15, ExerciseType.ISOLATION, WeightUnit.KG)!!
        assertEquals(13.0, kgS.weightKg!!, 1e-9)
    }

    @Test fun notAllSetsAtTopKeepsWeightAndAddsReps() {
        val s = Progression.suggest(sets(lb(135.0), 12, 11, 10), 8, 12, ExerciseType.COMPOUND, WeightUnit.LB)!!
        assertEquals(ProgressionAction.ADD_REPS, s.action)
        assertNull(s.weightKg)
        assertEquals(11, s.targetReps) // beat the weakest set by one
    }

    @Test fun warmupsDontBlockProgression() {
        val last = listOf(LoggedSet(5, lb(95.0), SetType.WARMUP)) + sets(lb(135.0), 12, 12)
        assertEquals(ProgressionAction.INCREASE_WEIGHT, Progression.suggest(last, 8, 12, ExerciseType.COMPOUND, WeightUnit.LB)!!.action)
    }

    @Test fun unloadedBodyweightSuggestsAddingLoad() {
        val s = Progression.suggest(sets(null, 15, 15, 15), 8, 15, ExerciseType.COMPOUND, WeightUnit.LB)!!
        assertEquals(ProgressionAction.ADD_LOAD, s.action)
        assertNull(s.weightKg)
    }

    @Test fun missingBottomTwiceDeloadsAboutTenPercent() {
        val last = sets(lb(200.0), 5, 4, 4)
        val prev = sets(lb(200.0), 5, 5, 4)
        val s = Progression.suggest(last, 6, 8, ExerciseType.COMPOUND, WeightUnit.LB, prev)!!
        assertEquals(ProgressionAction.DELOAD, s.action)
        assertEquals(180.0, Units.toDisplayWeight(s.weightKg!!, WeightUnit.LB), 1e-9)
        // Only once below the range: no deload yet.
        assertEquals(ProgressionAction.ADD_REPS, Progression.suggest(last, 6, 8, ExerciseType.COMPOUND, WeightUnit.LB, sets(lb(195.0), 8, 8, 7))!!.action)
    }

    @Test fun cardioAndEmptyGiveNothing() {
        assertNull(Progression.suggest(sets(null, 30), 20, 30, ExerciseType.CARDIO, WeightUnit.LB))
        assertNull(Progression.suggest(emptyList(), 8, 12, ExerciseType.COMPOUND, WeightUnit.LB))
        assertNull(Progression.suggest(listOf(LoggedSet(12, 50.0, done = false)), 8, 12, ExerciseType.COMPOUND, WeightUnit.LB))
    }
}
