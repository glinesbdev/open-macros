package dev.glinesb.openmacros.training

import dev.glinesb.openmacros.domain.DatedValue
import dev.glinesb.openmacros.domain.Units
import dev.glinesb.openmacros.domain.WeightUnit
import dev.glinesb.openmacros.domain.training.Equipment
import dev.glinesb.openmacros.domain.training.ExerciseLibrary
import dev.glinesb.openmacros.domain.training.ExerciseType
import dev.glinesb.openmacros.domain.training.LoggedSet
import dev.glinesb.openmacros.domain.training.Progression
import dev.glinesb.openmacros.domain.training.ProgressionAction
import dev.glinesb.openmacros.domain.training.Session
import dev.glinesb.openmacros.domain.training.Strength
import dev.glinesb.openmacros.domain.training.isBodyweightLoad
import dev.glinesb.openmacros.ui.training.setLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyweightTest {
    private fun lb(v: Double) = Units.fromDisplayWeight(v, WeightUnit.LB)

    @Test fun libraryTagsBodyweightExercises() {
        val dips = ExerciseLibrary.SEED.single { it.name == "Body Dips" }
        assertEquals(Equipment.BODYWEIGHT, dips.equipment)
        for (slug in listOf("dip", "pull-up", "chin-up", "push-up", "inverted-row", "pike-push-up")) {
            val e = ExerciseLibrary.bySlug.getValue(slug)
            assertTrue(slug, isBodyweightLoad(e.equipment, e.type, e.slug))
        }
        assertTrue(isBodyweightLoad(Equipment.MACHINE, ExerciseType.COMPOUND, "assisted-pull-up"))
        assertFalse(isBodyweightLoad(Equipment.BODYWEIGHT, ExerciseType.CARDIO, "running"))
        assertFalse(isBodyweightLoad(Equipment.BARBELL, ExerciseType.COMPOUND, "barbell-row"))
    }

    @Test fun display() {
        assertEquals("BW × 10", setLabel(10, null, WeightUnit.LB, bodyweight = true, cardio = false))
        assertEquals("BW + 25 lb × 8", setLabel(8, lb(25.0), WeightUnit.LB, bodyweight = true, cardio = false))
        assertEquals("BW − 20 kg × 6", setLabel(6, -20.0, WeightUnit.KG, bodyweight = true, cardio = false))
        assertEquals("135 lb × 5", setLabel(5, lb(135.0), WeightUnit.LB, bodyweight = false, cardio = false))
    }

    @Test fun loadsIncludeBodyweightFromLatestWeighIn() {
        val weighIns = listOf(DatedValue(100, 80.0), DatedValue(110, 82.0))
        assertEquals(80.0, Strength.bodyweightOn(105, weighIns)!!, 0.0)
        assertEquals(82.0, Strength.bodyweightOn(120, weighIns)!!, 0.0)
        assertEquals(80.0, Strength.bodyweightOn(90, weighIns)!!, 0.0) // before the first weigh-in: use the earliest
        assertNull(Strength.bodyweightOn(90, emptyList()))

        val sessions = listOf(
            Session(1, 105, listOf(LoggedSet(10, null), LoggedSet(8, 10.0))), // BW×10, BW+10 kg×8
            Session(2, 120, listOf(LoggedSet(6, -20.0))), // assisted: 82 − 20 = 62 kg
        )
        val eff = Strength.withBodyweight(sessions, weighIns)
        assertEquals(listOf(80.0, 90.0), eff[0].sets.map { it.weightKg })
        assertEquals(62.0, eff[1].sets.single().weightKg!!, 1e-9)
        val pr = Strength.records(eff)
        assertEquals(Strength.e1rm(90.0, 8), pr.bestE1rm!!.value, 1e-9)
        assertEquals(800.0 + 720.0, pr.bestVolume!!.value, 1e-9)

        // No weigh-ins: loads unknown, so only rep-based records remain.
        val noBw = Strength.records(Strength.withBodyweight(sessions, emptyList()))
        assertNull(noBw.bestE1rm); assertNull(noBw.heaviest); assertNull(noBw.bestVolume)
        assertEquals(10.0, noBw.mostReps!!.value, 0.0)
    }

    @Test fun progressionAddsLoadOrRemovesAssistance() {
        val plain = Progression.suggest(listOf(LoggedSet(12, null), LoggedSet(12, null)), 8, 12, ExerciseType.COMPOUND, WeightUnit.LB, bodyweight = true)!!
        assertEquals(ProgressionAction.INCREASE_WEIGHT, plain.action)
        assertEquals(5.0, Units.toDisplayWeight(plain.weightKg!!, WeightUnit.LB), 1e-9)
        assertTrue(plain.message, plain.message.contains("BW + 5 lb"))
        val assisted = Progression.suggest(listOf(LoggedSet(10, lb(-40.0))), 6, 10, ExerciseType.COMPOUND, WeightUnit.LB, bodyweight = true)!!
        assertEquals(-35.0, Units.toDisplayWeight(assisted.weightKg!!, WeightUnit.LB), 1e-9)
        assertTrue(assisted.message.contains("BW − 35 lb") && assisted.message.contains("less assistance"))
        val reps = Progression.suggest(listOf(LoggedSet(9, lb(25.0)), LoggedSet(7, lb(25.0))), 6, 10, ExerciseType.COMPOUND, WeightUnit.LB, bodyweight = true)!!
        assertEquals(ProgressionAction.ADD_REPS, reps.action)
        assertTrue(reps.message.contains("BW + 25 lb"))
    }
}
