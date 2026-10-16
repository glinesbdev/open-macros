package dev.glinesb.openmacros.photo

import dev.glinesb.openmacros.domain.Macros

/**
 * Estimates the foods and portions in a meal photo. Estimates are a starting point: the review
 * screen lets the user correct names, grams and macros (or swap in a database food) before logging.
 */
interface FoodPhotoEstimator {
    /** False when there's no key configured; the UI then explains how to enable photo logging. */
    val isAvailable: Boolean

    /** @throws PhotoEstimateException on network, quota, or parse failures. */
    suspend fun estimate(photo: FoodPhoto): PhotoEstimate
}

/** A (downscaled) JPEG of the plate plus optional user context ("half eaten", "cooked in butter"). */
class FoodPhoto(val jpeg: ByteArray, val mimeType: String = "image/jpeg", val hint: String? = null)

data class PhotoEstimate(
    val items: List<EstimatedFood>,
    /** Free-text caveats from the model (e.g. "sauce not visible, assumed none"). */
    val notes: String? = null,
    /** What the request cost, when the API reports it. */
    val costUsd: Double? = null,
)

data class EstimatedFood(
    val name: String,
    val estimatedGrams: Double?,
    val macros: Macros,
    /** 0..1, model-reported; low values are visually flagged. */
    val confidence: Float,
)

class PhotoEstimateException(message: String, cause: Throwable? = null) : Exception(message, cause)
