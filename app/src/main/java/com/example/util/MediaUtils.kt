package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

object MediaUtils {
    private val bitmapCache = object : LruCache<String, ImageBitmap>(60) {}

    fun decodeBase64Image(base64: String?): ImageBitmap? {
        if (base64.isNullOrBlank()) return null
        val cacheKey = base64.hashCode().toString() + "_" + base64.length
        bitmapCache.get(cacheKey)?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val imageBitmap = bitmap.asImageBitmap()
            bitmapCache.put(cacheKey, imageBitmap)
            imageBitmap
        } catch (_: Exception) {
            null
        }
    }

    suspend fun compressImageToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 720,
        maxBase64Chars: Int = 550_000
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val origW = boundsOpts.outWidth
            val origH = boundsOpts.outHeight
            if (origW <= 0 || origH <= 0) {
                return@withContext Result.failure(IllegalArgumentException("No se pudo leer la imagen seleccionada"))
            }

            var sampleSize = 1
            while (max(origW / sampleSize, origH / sampleSize) > maxDimension * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@withContext Result.failure(IllegalArgumentException("Error al decodificar la imagen"))

            val scale = min(1f, maxDimension.toFloat() / max(decoded.width, decoded.height).toFloat())
            val scaled = if (scale < 1f) {
                val matrix = Matrix().apply { postScale(scale, scale) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                    if (it != decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            var quality = 78
            var encoded = ""
            while (quality >= 30) {
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                if (encoded.length <= maxBase64Chars) break
                quality -= 12
            }
            scaled.recycle()

            if (encoded.length > maxBase64Chars) {
                Result.failure(IllegalArgumentException("La imagen es demasiado pesada para subir."))
            } else {
                Result.success(encoded)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class PreparedVideo(
        val chunksBase64: List<String>,
        val thumbnailBase64: String?
    )

    suspend fun prepareVideoChunks(
        context: Context,
        uri: Uri,
        maxRawBytes: Int = 6 * 1024 * 1024, // 6 MB max (~15-30s mobile clip)
        chunkRawBytes: Int = 420 * 1024 // ~560 KB Base64 (< 750 KB rule limit)
    ): Result<PreparedVideo> = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalArgumentException("No se pudo abrir el video"))

            if (bytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("El video está vacío"))
            }
            if (bytes.size > maxRawBytes) {
                return@withContext Result.failure(
                    IllegalArgumentException("El video supera los 6 MB. Elige un clip más corto para compartirlo en línea.")
                )
            }

            val thumbnailBase64 = extractVideoThumbnailBase64(context, uri)
            val chunks = mutableListOf<String>()
            var offset = 0
            while (offset < bytes.size) {
                val end = min(offset + chunkRawBytes, bytes.size)
                val slice = bytes.copyOfRange(offset, end)
                chunks.add(Base64.encodeToString(slice, Base64.NO_WRAP))
                offset = end
            }
            if (chunks.size > 20) {
                return@withContext Result.failure(
                    IllegalArgumentException("El video excede el límite permitido de fragmentos.")
                )
            }
            Result.success(PreparedVideo(chunksBase64 = chunks, thumbnailBase64 = thumbnailBase64))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractVideoThumbnailBase64(context: Context, uri: Uri): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            val scale = min(1f, 540f / max(frame.width, frame.height).toFloat())
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    frame,
                    max(1, (frame.width * scale).toInt()),
                    max(1, (frame.height * scale).toInt()),
                    true
                ).also { if (it != frame) frame.recycle() }
            } else frame
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            scaled.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun writeChunksToCacheFile(
        cacheDir: File,
        postId: String,
        chunksBase64: List<String>
    ): File = withContext(Dispatchers.IO) {
        val videoDir = File(cacheDir, "momentia_videos").apply { mkdirs() }
        val targetFile = File(videoDir, "$postId.mp4")
        val tmpFile = File(videoDir, "$postId.mp4.tmp")
        tmpFile.outputStream().use { out ->
            for (chunk in chunksBase64) {
                val decoded = Base64.decode(chunk, Base64.DEFAULT)
                out.write(decoded)
            }
            out.flush()
        }
        if (targetFile.exists()) targetFile.delete()
        tmpFile.renameTo(targetFile)
        targetFile
    }

    fun formatRelativeTime(timestamp: Timestamp?): String {
        val ts = timestamp ?: return "ahora"
        val diffSeconds = max(0L, (System.currentTimeMillis() - ts.toDate().time) / 1000L)
        return when {
            diffSeconds < 60 -> "ahora"
            diffSeconds < 3600 -> "${diffSeconds / 60} min"
            diffSeconds < 86400 -> "${diffSeconds / 3600} h"
            diffSeconds < 604800 -> "${diffSeconds / 86400} d"
            else -> "${diffSeconds / 604800} sem"
        }
    }
}
