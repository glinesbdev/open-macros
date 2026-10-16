package dev.glinesb.openmacros.photo

import dev.glinesb.openmacros.domain.Macros

/**
 * Seam for the next phase: estimate the foods and portions in a meal photo.
 *
 * The planned implementation sends the photo to a vision model (e.g. Grok through the xAI API,
 * with the user's own key, stored like the USDA key) and returns editable estimates. Estimates are
 * a starting point: the UI must let the user correct names, grams and macros before anything is logged.
 *
 * Not implemented in v1: [AppContainer][dev.glinesb.openmacros.AppContainer] wires up
 * [UnavailableFoodPhotoEstimator], and no UI calls this yet.
 */
interface FoodPhotoEstimator {
    /** False when there's no backend/key configured; the UI should hide photo logging. */
    val isAvailable: Boolean

    /** @throws PhotoEstimateException on network, quota, or parse failures. */
    suspend fun estimate(photo: FoodPhoto): PhotoEstimate
}

/** A JPEG of the plate plus optional user context ("half eaten", "restaurant portion"). */
class FoodPhoto(val jpeg: ByteArray, val mimeType: String = "image/jpeg", val hint: String? = null)

data class PhotoEstimate(
    val items: List<EstimatedFood>,
    /** Free-text caveats from the model (e.g. "sauce not visible, assumed none"). */
    val notes: String? = null,
)

data class EstimatedFood(
    val name: String,
    val estimatedGrams: Double?,
    val macros: Macros,
    /** 0..1, model-reported; low values should be visually flagged. */
    val confidence: Float,
)

class PhotoEstimateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** v1 placeholder. */
object UnavailableFoodPhotoEstimator : FoodPhotoEstimator {
    override val isAvailable = false
    override suspend fun estimate(photo: FoodPhoto): PhotoEstimate =
        throw PhotoEstimateException("Photo logging isn't available yet")
}
