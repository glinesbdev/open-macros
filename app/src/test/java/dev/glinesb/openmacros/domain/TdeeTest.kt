package dev.glinesb.openmacros.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TdeeTest {
    private fun profile(
        sex: Sex = Sex.MALE, age: Int = 30, cm: Double = 180.0, kg: Double = 80.0,
        activity: ActivityLevel = ActivityLevel.MODERATE, goal: GoalType = GoalType.MAINTAIN, rate: Double = 0.0,
    ) = Profile(sex, age, cm, kg, activity, goal, rate)

    @Test fun bmrMaleMatchesMifflinStJeor() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 800 + 1125 - 150 + 5
        assertEquals(1780.0, Tdee.bmr(Sex.MALE, 80.0, 180.0, 30), 1e-9)
    }

    @Test fun bmrFemaleMatchesMifflinStJeor() {
        // 10*60 + 6.25*165 - 5*25 - 161 = 600 + 1031.25 - 125 - 161
        assertEquals(1345.25, Tdee.bmr(Sex.FEMALE, 60.0, 165.0, 25), 1e-9)
    }

    @Test fun maleAndFemaleDifferBy166() {
        assertEquals(166.0, Tdee.bmr(Sex.MALE, 70.0, 170.0, 40) - Tdee.bmr(Sex.FEMALE, 70.0, 170.0, 40), 1e-9)
    }

    @Test fun tdeeAppliesActivityMultiplier() {
        assertEquals(1780.0 * 1.55, Tdee.tdee(profile()), 1e-9)
        assertEquals(1780.0 * 1.2, Tdee.tdee(profile(activity = ActivityLevel.SEDENTARY)), 1e-9)
        assertEquals(1780.0 * 1.9, Tdee.tdee(profile(activity = ActivityLevel.EXTRA)), 1e-9)
    }

    @Test fun maintainTargetIsRoundedTdee() {
        assertEquals(2759, Tdee.calorieTarget(profile())) // 2759.0
    }

    @Test fun losingHalfKgPerWeekSubtracts550() {
        assertEquals(-550.0, Tdee.dailyAdjustment(GoalType.LOSE, 0.5), 1e-9)
        assertEquals(2209, Tdee.calorieTarget(profile(goal = GoalType.LOSE, rate = 0.5)))
    }

    @Test fun gainingAddsSurplus() {
        assertEquals(275.0, Tdee.dailyAdjustment(GoalType.GAIN, 0.25), 1e-9)
        assertEquals(3034, Tdee.calorieTarget(profile(goal = GoalType.GAIN, rate = 0.25)))
    }

    @Test fun rateSignIgnoredDirectionComesFromGoal() {
        assertEquals(Tdee.dailyAdjustment(GoalType.LOSE, 0.5), Tdee.dailyAdjustment(GoalType.LOSE, -0.5), 1e-9)
        assertEquals(0.0, Tdee.dailyAdjustment(GoalType.MAINTAIN, 1.0), 1e-9)
    }

    @Test fun onePoundPerWeekIsAbout500Kcal() {
        assertEquals(500.0, -Tdee.dailyAdjustment(GoalType.LOSE, Units.lbToKg(1.0)), 2.0)
    }

    @Test fun targetIsFlooredForAggressiveDeficits() {
        val small = profile(sex = Sex.FEMALE, age = 60, cm = 150.0, kg = 50.0, activity = ActivityLevel.SEDENTARY,
            goal = GoalType.LOSE, rate = 1.0)
        assertEquals(Tdee.MIN_CALORIES, Tdee.calorieTarget(small))
        assertTrue(Tdee.isFloored(small))
        assertFalse(Tdee.isFloored(profile()))
    }

    @Test fun macroSplitGrams() {
        val g = MacroSplit(30, 40, 30).grams(2000)
        assertEquals(150, g.protein) // 600 kcal / 4
        assertEquals(200, g.carbs) // 800 kcal / 4
        assertEquals(67, g.fat) // 600 kcal / 9 = 66.7
    }

    @Test fun macroSplitValidity() {
        assertTrue(MacroSplit.DEFAULT.isValid)
        assertFalse(MacroSplit(30, 40, 20).isValid)
        assertFalse(MacroSplit(-10, 60, 50).isValid)
    }
}
