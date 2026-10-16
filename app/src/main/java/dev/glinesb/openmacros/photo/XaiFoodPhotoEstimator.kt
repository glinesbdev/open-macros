package dev.glinesb.openmacros.photo

import dev.glinesb.openmacros.data.SecureKeys
import dev.glinesb.openmacros.data.remote.Http
import dev.glinesb.openmacros.data.remote.longClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Photo → estimate via xAI chat completions (OpenAI-compatible, stateless: nothing is stored
 * server-side for later retrieval). Uses the user's own key; every call is billed to their xAI account.
 * The key is sent only in the Authorization header and is never logged or put in exception messages.
 */
class XaiFoodPhotoEstimator(
    private val keys: SecureKeys,
    private val endpoint: String = ENDPOINT,
    private val model: String = MODEL,
) : FoodPhotoEstimator {
    override val isAvailable: Boolean get() = keys.xaiKey != null

    override suspend fun estimate(photo: FoodPhoto): PhotoEstimate {
        val key = keys.xaiKey ?: throw PhotoEstimateException("Add your xAI API key in Settings to use photo logging")
        return try {
            send(key, photo, structured = true)
        } catch (e: StructuredOutputUnsupported) {
            // Older/other models may reject response_format; the prompt alone still asks for strict JSON.
            send(key, photo, structured = false)
        }
    }

    private class StructuredOutputUnsupported : Exception()

    private suspend fun send(key: String, photo: FoodPhoto, structured: Boolean): PhotoEstimate = withContext(Dispatchers.IO) {
        val body = PhotoEstimateParser.buildRequest(model, photo, structured).toString()
        val req = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            longClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val detail = PhotoEstimateParser.errorMessage(text)?.replace(key, "***")
                    if (structured && resp.code in 400..422 && detail?.contains("response_format", ignoreCase = true) == true) {
                        throw StructuredOutputUnsupported()
                    }
                    // xAI answers a bad key with 400 "Incorrect API key provided", not 401.
                    val badKey = resp.code in listOf(400, 401, 403) && detail?.contains("API key", ignoreCase = true) == true
                    throw PhotoEstimateException(
                        when {
                            badKey -> "xAI rejected the API key. Check it in Settings."
                            else -> when (resp.code) {
                            400 -> "xAI couldn't process the photo" + (detail?.let { ": $it" } ?: "")
                            401, 403 -> "xAI rejected the API key. Check it in Settings."
                            404 -> "The model $model isn't available to this key"
                            429 -> "xAI rate limit or spending limit reached. Try again later."
                            in 500..599 -> "xAI is having trouble (${resp.code}). Try again."
                            else -> "xAI error ${resp.code}" + (detail?.let { ": $it" } ?: "")
                            }
                        },
                    )
                }
                val root = try {
                    Http.json.parseToJsonElement(text) as JsonObject
                } catch (e: Exception) {
                    throw PhotoEstimateException("Unexpected response from xAI")
                }
                PhotoEstimateParser.parseCompletion(root)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PhotoEstimateException) {
            throw e
        } catch (e: StructuredOutputUnsupported) {
            throw e
        } catch (e: InterruptedIOException) {
            throw PhotoEstimateException("xAI took too long to answer. Try again.", e)
        } catch (e: IOException) {
            throw PhotoEstimateException("Network error. Check your connection.", e)
        }
    }

    companion object {
        const val ENDPOINT = "https://api.x.ai/v1/chat/completions"

        /** Vision-capable (text + image → text) Grok model per docs.x.ai, Oct 2026. */
        const val MODEL = "grok-4.7"
    }
}
