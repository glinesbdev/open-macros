package dev.glinesb.openmacros.domain

/** Energy (kcal) and macronutrients (grams). */
data class Macros(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat)
    operator fun times(f: Double) = Macros(kcal * f, protein * f, carbs * f, fat * f)

    companion object {
        val ZERO = Macros(0.0, 0.0, 0.0, 0.0)

        /** Atwater estimate of energy from macros. */
        fun kcalFrom(protein: Double, carbs: Double, fat: Double) =
            protein * MacroSplit.KCAL_PER_G_PROTEIN + carbs * MacroSplit.KCAL_PER_G_CARBS + fat * MacroSplit.KCAL_PER_G_FAT
    }
}

enum class Meal(val label: String) { BREAKFAST("Breakfast"), LUNCH("Lunch"), DINNER("Dinner"), SNACKS("Snacks") }

enum class FoodSource(val label: String) { OPEN_FOOD_FACTS("Open Food Facts"), USDA("USDA"), CUSTOM("Custom") }

/**
 * A food as it comes from a source, normalised to one serving.
 * [servingGrams] is null when the serving has no known mass (then only "serving" units work).
 */
data class FoodItem(
    val source: FoodSource,
    val sourceId: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val servingLabel: String,
    val servingGrams: Double?,
    val perServing: Macros,
)

enum class AmountUnit(val label: String) { SERVING("serving"), GRAM("g"), OUNCE("oz") }

object Portions {
    /** Units that make sense for a food: grams/ounces need a known serving mass. */
    fun unitsFor(servingGrams: Double?): List<AmountUnit> =
        if (servingGrams != null && servingGrams > 0) AmountUnit.entries else listOf(AmountUnit.SERVING)

    /** How many servings [amount] of [unit] is. */
    fun servings(amount: Double, unit: AmountUnit, servingGrams: Double?): Double = when (unit) {
        AmountUnit.SERVING -> amount
        AmountUnit.GRAM -> if (servingGrams != null && servingGrams > 0) amount / servingGrams else amount
        AmountUnit.OUNCE -> if (servingGrams != null && servingGrams > 0) amount * Units.G_PER_OZ / servingGrams else amount
    }
}
