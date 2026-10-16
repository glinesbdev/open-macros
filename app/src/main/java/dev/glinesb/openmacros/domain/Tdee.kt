package dev.glinesb.openmacros.domain

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Sex as used by the Mifflin-St Jeor equation (it only has two variants). */
enum class Sex { MALE, FEMALE }

enum class ActivityLevel(val multiplier: Double, val label: String, val description: String) {
    SEDENTARY(1.2, "Sedentary", "Desk job, little or no exercise"),
    LIGHT(1.375, "Lightly active", "Light exercise 1–3 days a week"),
    MODERATE(1.55, "Moderately active", "Exercise 3–5 days a week"),
    VERY(1.725, "Very active", "Hard exercise 6–7 days a week"),
    EXTRA(1.9, "Extra active", "Physical job or training twice a day"),
}

enum class GoalType(val label: String) { LOSE("Lose"), MAINTAIN("Maintain"), GAIN("Gain") }

/** Everything the calorie target is computed from. Stored in metric. */
@Serializable
data class Profile(
    val sex: Sex,
    val ageYears: Int,
    val heightCm: Double,
    val weightKg: Double,
    val activity: ActivityLevel,
    val goal: GoalType,
    /** Target rate of change in kg per week, always >= 0; direction comes from [goal]. */
    val rateKgPerWeek: Double,
)

object Tdee {
    /** Energy in one kg of body-weight change (the common ~3500 kcal/lb rule ≈ 7700 kcal/kg). */
    const val KCAL_PER_KG = 7700.0

    /** Never suggest a target below this; very low intakes need medical supervision. */
    const val MIN_CALORIES = 1200

    /** Mifflin-St Jeor (1990): 10·kg + 6.25·cm − 5·age + 5 (male) or − 161 (female). */
    fun bmr(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return when (sex) {
            Sex.MALE -> base + 5.0
            Sex.FEMALE -> base - 161.0
        }
    }

    fun bmr(p: Profile): Double = bmr(p.sex, p.weightKg, p.heightCm, p.ageYears)

    /** Total daily energy expenditure: BMR × activity multiplier. */
    fun tdee(p: Profile): Double = bmr(p) * p.activity.multiplier

    /** Daily kcal surplus (+) or deficit (−) needed for the chosen weekly rate. */
    fun dailyAdjustment(goal: GoalType, rateKgPerWeek: Double): Double = when (goal) {
        GoalType.MAINTAIN -> 0.0
        GoalType.LOSE -> -abs(rateKgPerWeek) * KCAL_PER_KG / 7.0
        GoalType.GAIN -> abs(rateKgPerWeek) * KCAL_PER_KG / 7.0
    }

    /** Raw target before the safety floor. */
    fun rawCalorieTarget(p: Profile): Double = tdee(p) + dailyAdjustment(p.goal, p.rateKgPerWeek)

    /** Rounded daily calorie target, floored at [MIN_CALORIES]. */
    fun calorieTarget(p: Profile): Int = max(MIN_CALORIES, rawCalorieTarget(p).roundToInt())

    /** True when the floor kicked in (the chosen rate is too aggressive). */
    fun isFloored(p: Profile): Boolean = rawCalorieTarget(p).roundToInt() < MIN_CALORIES
}

/** Macro split as whole percentages of calories. Must add up to 100. */
@Serializable
data class MacroSplit(val proteinPct: Int, val carbsPct: Int, val fatPct: Int) {
    val isValid: Boolean
        get() = proteinPct >= 0 && carbsPct >= 0 && fatPct >= 0 && proteinPct + carbsPct + fatPct == 100

    /** Gram targets for a calorie target (4 kcal/g protein and carbs, 9 kcal/g fat). */
    fun grams(calories: Int): MacroGrams = MacroGrams(
        protein = (calories * proteinPct / 100.0 / KCAL_PER_G_PROTEIN).roundToInt(),
        carbs = (calories * carbsPct / 100.0 / KCAL_PER_G_CARBS).roundToInt(),
        fat = (calories * fatPct / 100.0 / KCAL_PER_G_FAT).roundToInt(),
    )

    companion object {
        const val KCAL_PER_G_PROTEIN = 4.0
        const val KCAL_PER_G_CARBS = 4.0
        const val KCAL_PER_G_FAT = 9.0
        val DEFAULT = MacroSplit(proteinPct = 30, carbsPct = 40, fatPct = 30)
    }
}

data class MacroGrams(val protein: Int, val carbs: Int, val fat: Int)
