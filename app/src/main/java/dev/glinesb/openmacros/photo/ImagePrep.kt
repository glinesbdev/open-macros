package dev.glinesb.openmacros.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object ImageSizing {
    const val MAX_EDGE = 1024

    /** Size that fits within [maxEdge] on the long side, keeping aspect ratio; never upscales. */
    fun fit(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val long = max(width, height)
        if (long <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / long
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }
}

/**
 * Decodes a photo (camera file or gallery URI), downscales to ~1024 px on the long edge and
 * re-encodes as JPEG. ImageDecoder (API 28+) applies EXIF rotation and drops metadata such as GPS.
 */
object ImagePrep {
    const val JPEG_QUALITY = 85

    suspend fun fromUri(resolver: ContentResolver, uri: Uri): ByteArray =
        withContext(Dispatchers.IO) { encode(ImageDecoder.createSource(resolver, uri)) }

    suspend fun fromFile(file: File): ByteArray =
        withContext(Dispatchers.IO) { encode(ImageDecoder.createSource(file)) }

    private fun encode(source: ImageDecoder.Source): ByteArray {
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val (w, h) = ImageSizing.fit(info.size.width, info.size.height)
            decoder.setTargetSize(w, h)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return try {
            ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "JPEG encoding failed" }
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
