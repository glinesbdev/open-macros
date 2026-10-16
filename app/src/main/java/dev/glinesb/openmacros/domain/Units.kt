package dev.glinesb.openmacros.domain

enum class WeightUnit(val symbol: String) { LB("lb"), KG("kg") }

enum class HeightUnit { CM, FT_IN }

object Units {
    const val LB_PER_KG = 2.2046226218
    const val CM_PER_IN = 2.54
    const val G_PER_OZ = 28.349523125

    fun kgToLb(kg: Double) = kg * LB_PER_KG
    fun lbToKg(lb: Double) = lb / LB_PER_KG

    fun toDisplayWeight(kg: Double, unit: WeightUnit) = if (unit == WeightUnit.KG) kg else kgToLb(kg)
    fun fromDisplayWeight(value: Double, unit: WeightUnit) = if (unit == WeightUnit.KG) value else lbToKg(value)

    fun cmToFeetInches(cm: Double): Pair<Int, Double> {
        val totalIn = cm / CM_PER_IN
        val feet = (totalIn / 12).toInt()
        return feet to (totalIn - feet * 12)
    }

    fun feetInchesToCm(feet: Int, inches: Double) = (feet * 12 + inches) * CM_PER_IN
}
