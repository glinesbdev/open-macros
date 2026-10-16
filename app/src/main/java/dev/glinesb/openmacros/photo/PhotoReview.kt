package dev.glinesb.openmacros.photo

import dev.glinesb.openmacros.domain.FoodItem
import dev.glinesb.openmacros.domain.Macros

/**
 * One editable line on the review screen. Changing [grams] scales the macros proportionally;
 * editing a macro directly just sets it (later gram changes scale from the edited values).
 */
data class ReviewItem(
    /** Stable identity for list keys while the user edits. */
    val id: Long = nextId.incrementAndGet(),
    val name: String,
    val grams: Double?,
    val macros: Macros,
    val confidence: Float?,
    /** Set when the user swapped the estimate for a database food (cached in the foods table). */
    val foodId: Long? = null,
    val brand: String? = null,
    val include: Boolean = true,
) {
    fun withGrams(newGrams: Double): ReviewItem {
        if (newGrams < 0 || !newGrams.isFinite()) return this
        val old = grams
        val scaled = if (old != null && old > 0) macros * (newGrams / old) else macros
        return copy(grams = newGrams, macros = scaled)
    }

    fun withMacros(m: Macros): ReviewItem = copy(macros = m)

    /**
     * Replace with a database food, keeping the estimated portion: if the food's serving mass is
     * known, macros come from the food scaled to [grams]; otherwise one serving of the food.
     */
    fun swappedFor(food: FoodItem, foodId: Long): ReviewItem {
        val g = grams
        val sg = food.servingGrams
        return if (g != null && g > 0 && sg != null && sg > 0) {
            copy(name = food.name, brand = food.brand, foodId = foodId, macros = food.perServing * (g / sg), confidence = null)
        } else {
            copy(name = food.name, brand = food.brand, foodId = foodId, grams = sg, macros = food.perServing, confidence = null)
        }
    }

    companion object {
        private val nextId = java.util.concurrent.atomic.AtomicLong()
        fun from(e: EstimatedFood) = ReviewItem(name = e.name, grams = e.estimatedGrams, macros = e.macros, confidence = e.confidence)
    }
}

enum class ConfidenceLevel(val label: String) { HIGH("High"), MEDIUM("Medium"), LOW("Low") }

fun confidenceLevel(c: Float): ConfidenceLevel = when {
    c >= 0.75f -> ConfidenceLevel.HIGH
    c >= 0.45f -> ConfidenceLevel.MEDIUM
    else -> ConfidenceLevel.LOW
}
