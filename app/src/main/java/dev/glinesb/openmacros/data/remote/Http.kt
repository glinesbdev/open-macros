package dev.glinesb.openmacros.data.remote

import dev.glinesb.openmacros.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Thrown for non-2xx responses so the UI can show something specific (e.g. USDA rate limit). */
class HttpStatusException(val code: Int, message: String) : IOException(message)

object Http {
    /** Open Food Facts asks every app to identify itself: AppName/Version (contact). */
    val USER_AGENT = "OpenMacros/${BuildConfig.VERSION_NAME} (Android; https://github.com/glinesbdev/open-macros)"

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
    }

    /** GET and parse a JSON body. [redact] hides secrets (API keys) from exception messages. */
    suspend fun getJson(url: String, redact: String? = null): JsonElement = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                var safeUrl = url.substringBefore('?')
                if (redact != null) safeUrl = safeUrl.replace(redact, "***")
                throw HttpStatusException(resp.code, "HTTP ${resp.code} from $safeUrl")
            }
            json.parseToJsonElement(body)
        }
    }
}

/** Lenient numeric read: sources mix numbers and numeric strings. */
internal fun JsonObject.num(key: String): Double? {
    val p = this[key] as? JsonPrimitive ?: return null
    return p.contentOrNull?.trim()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it.isFinite() }
}

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
