package dev.glinesb.openmacros.training

import dev.glinesb.openmacros.domain.GoalType
import dev.glinesb.openmacros.domain.training.EquipmentAccess
import dev.glinesb.openmacros.domain.training.ExerciseLibrary
import dev.glinesb.openmacros.domain.training.ExerciseType
import dev.glinesb.openmacros.domain.training.Experience
import dev.glinesb.openmacros.domain.training.Muscle
import dev.glinesb.openmacros.domain.training.SplitInput
import dev.glinesb.openmacros.domain.training.SplitRecommender
import dev.glinesb.openmacros.domain.training.TrainingGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitRecommenderTest {
    private val allInputs = (2..6).flatMap { d ->
        TrainingGoal.entries.flatMap { g -> Experience.entries.flatMap { x -> EquipmentAccess.entries.map { e -> SplitInput(d, g, x, e) } } }
    }

    @Test fun everyCombinationIsWellFormed() {
        for (input in allInputs) {
            val recs = SplitRecommender.recommend(input)
            assertTrue("$input has options", recs.size >= 2)
            assertEquals("$input: exactly one recommended", 1, recs.count { it.recommended })
            for (r in recs) {
                assertEquals("$input ${r.id}", input.daysPerWeek, r.days.size)
                assertTrue(r.why.isNotBlank() && r.goalNote.isNotBlank())
                for (d in r.days) {
                    assertTrue("${r.id}/${d.name} has exercises", d.exercises.size >= 3)
                    assertEquals("no duplicates in ${d.name}", d.exercises.size, d.exercises.map { it.slug }.distinct().size)
                    for (p in d.exercises) {
                        val ex = ExerciseLibrary.bySlug[p.slug]
                        assertTrue("${p.slug} is in the library", ex != null)
                        assertTrue("${p.slug} fits ${input.equipment}", ex!!.equipment in input.equipment.allowed)
                        assertTrue(p.sets in 1..6 && p.repMin in 1..p.repMax && p.restSeconds in 30..300)
                    }
                }
            }
        }
    }

    @Test fun fourDaysDefaultsToUpperLowerWithPplPlusFullBodyOffered() {
        val recs = SplitRecommender.recommend(SplitInput())
        assertEquals("ul2", recs.first().id)
        assertEquals(listOf("Upper A", "Lower A", "Upper B", "Lower B"), recs.first().days.map { it.name })
        assertTrue(recs.any { it.id == "pplf" })
    }

    @Test fun goalShapesRepRanges() {
        fun mainLift(goal: TrainingGoal) = SplitRecommender.recommend(SplitInput(4, goal)).first().days.first().exercises.first()
        val strength = mainLift(TrainingGoal.STRENGTH)
        assertEquals(3, strength.repMin); assertEquals(5, strength.repMax); assertEquals(180, strength.restSeconds)
        val bulk = mainLift(TrainingGoal.MUSCLE_GAIN)
        assertEquals(6, bulk.repMin); assertEquals(10, bulk.repMax)
        val cut = mainLift(TrainingGoal.FAT_LOSS)
        assertEquals(5, cut.repMin); assertEquals(8, cut.repMax)
        // Strength uses the conventional deadlift on lower B; hypertrophy goals use RDLs.
        assertEquals("deadlift", SplitRecommender.recommend(SplitInput(4, TrainingGoal.STRENGTH)).first().days[3].exercises.first().slug)
        assertEquals("romanian-deadlift", SplitRecommender.recommend(SplitInput(4, TrainingGoal.MUSCLE_GAIN)).first().days[3].exercises.first().slug)
    }

    @Test fun cutHasLessVolumeThanBulk() {
        fun total(goal: TrainingGoal) = SplitRecommender.recommend(SplitInput(4, goal)).first().days.sumOf { d -> d.exercises.sumOf { it.sets } }
        assertTrue(total(TrainingGoal.FAT_LOSS) < total(TrainingGoal.MUSCLE_GAIN))
    }

    @Test fun weeklyVolumeIsInTheEvidenceRangeForIntermediateBulk() {
        val weekly = SplitRecommender.recommend(SplitInput(4, TrainingGoal.MUSCLE_GAIN, Experience.INTERMEDIATE)).first().weeklySets()
        for (m in listOf(Muscle.CHEST, Muscle.LATS, Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.SIDE_DELTS, Muscle.BICEPS, Muscle.TRICEPS)) {
            val v = weekly[m] ?: 0.0
            assertTrue("$m weekly sets $v", v in 6.0..25.0)
        }
    }

    @Test fun beginnersGetFewerSetsAndExercises() {
        val beg = SplitRecommender.recommend(SplitInput(4, TrainingGoal.STRENGTH, Experience.BEGINNER)).first()
        val adv = SplitRecommender.recommend(SplitInput(4, TrainingGoal.STRENGTH, Experience.ADVANCED)).first()
        assertTrue(beg.days.all { d -> d.exercises.all { it.sets <= 3 } })
        assertTrue(beg.days.sumOf { it.exercises.size } < adv.days.sumOf { it.exercises.size })
        assertTrue(SplitRecommender.recommend(SplitInput(6, experience = Experience.BEGINNER)).first().why.contains("beginner"))
        assertEquals("ul3", SplitRecommender.recommend(SplitInput(6, experience = Experience.BEGINNER)).first().id)
        assertEquals("ppl2", SplitRecommender.recommend(SplitInput(6, experience = Experience.ADVANCED)).first().id)
    }

    @Test fun bodyweightUsesHigherReps() {
        val day = SplitRecommender.recommend(SplitInput(3, TrainingGoal.MUSCLE_GAIN, equipment = EquipmentAccess.BODYWEIGHT)).first().days.first()
        val pushUp = day.exercises.first { it.slug == "push-up" }
        assertEquals(8, pushUp.repMin); assertEquals(15, pushUp.repMax)
        assertTrue(day.exercises.none { ExerciseLibrary.bySlug.getValue(it.slug).type == ExerciseType.CARDIO })
    }

    @Test fun nutritionOffers() {
        val cut = SplitRecommender.nutritionFor(TrainingGoal.FAT_LOSS, Experience.INTERMEDIATE, 80.0)!!
        assertEquals(GoalType.LOSE, cut.goal); assertEquals(0.6, cut.rateKgPerWeek, 1e-9) // 0.75% of 80 kg
        val bulk = SplitRecommender.nutritionFor(TrainingGoal.MUSCLE_GAIN, Experience.BEGINNER, 80.0)!!
        assertEquals(GoalType.GAIN, bulk.goal); assertEquals(0.4, bulk.rateKgPerWeek, 1e-9)
        val bulkAdv = SplitRecommender.nutritionFor(TrainingGoal.MUSCLE_GAIN, Experience.ADVANCED, 80.0)!!
        assertEquals(0.2, bulkAdv.rateKgPerWeek, 1e-9)
        assertEquals(GoalType.MAINTAIN, SplitRecommender.nutritionFor(TrainingGoal.RECOMP, Experience.BEGINNER, 80.0)!!.goal)
        assertNull(SplitRecommender.nutritionFor(TrainingGoal.STRENGTH, Experience.BEGINNER, 80.0))
        // Clamped for very light or heavy people.
        assertEquals(1.0, SplitRecommender.nutritionFor(TrainingGoal.FAT_LOSS, Experience.BEGINNER, 200.0)!!.rateKgPerWeek, 1e-9)
    }
}
