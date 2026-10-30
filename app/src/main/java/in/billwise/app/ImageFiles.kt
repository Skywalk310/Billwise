package `in`.billwise.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * App-private image storage for user-picked logo / stamp and drawn signatures.
 * Everything stays in filesDir — never uploaded anywhere.
 */
object ImageFiles {

    private fun dir(context: Context, name: String): File {
        val d = File(context.filesDir, name)
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** Result of copying a user-picked image, with a specific failure reason. */
    sealed class PickResult {
        data class Ok(val file: File) : PickResult()
        /** The content URI stream could not be opened at all (e.g. a cloud-only item). */
        object StreamFailed : PickResult()
        /** Bytes were read, but neither decoder could make sense of them. */
        object DecodeFailed : PickResult()
    }

    /**
     * Decode a picked gallery image, downscale to at most 1024 px on the longest side,
     * save as PNG in app-private storage.
     *
     * The content stream is opened ONCE and read fully into memory: some providers
     * fail or return different data on a second open, and BitmapFactory cannot decode
     * HEIC/HEIF/AVIF at all, so on API 28+ a failed BitmapFactory decode falls back
     * to ImageDecoder (which handles those formats).
     */
    fun copyPickedImage(context: Context, uri: Uri, subdir: String, fileName: String): PickResult {
        val bytes: ByteArray = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return PickResult.StreamFailed

        var bmp: Bitmap? = null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                var sample = 1
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
                while (maxSide / sample > 1024 && sample < 64) sample *= 2
                bmp = BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }
        } catch (e: Exception) {
            bmp = null
        }

        if (bmp == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bmp = try {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val maxSide = maxOf(info.size.width, info.size.height)
                    if (maxSide > 1024) {
                        val sample = (maxSide + 1023) / 1024
                        decoder.setTargetSize(
                            (info.size.width / sample).coerceAtLeast(1),
                            (info.size.height / sample).coerceAtLeast(1)
                        )
                    }
                }
            } catch (e: Exception) {
                null
            }
        }

        if (bmp == null) return PickResult.DecodeFailed
        return try {
            val out = File(dir(context, subdir), fileName)
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!bmp.isRecycled) bmp.recycle()
            PickResult.Ok(out)
        } catch (e: Exception) {
            PickResult.DecodeFailed
        }
    }

    fun saveBitmap(context: Context, bitmap: Bitmap, subdir: String, fileName: String): File? {
        return try {
            val out = File(dir(context, subdir), fileName)
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            out
        } catch (e: Exception) {
            null
        }
    }

    fun deleteIfExists(path: String) {
        try {
            if (path.isNotBlank()) {
                val f = File(path)
                if (f.exists()) f.delete()
            }
        } catch (e: Exception) {
        }
    }

    /** Decode a stored image, downscaled so its longest side is at most [maxDim] px. Null if missing/unreadable. */
    fun loadScaled(path: String, maxDim: Int = 512): Bitmap? {
        return try {
            val f = File(path)
            if (!f.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxSide / sample > maxDim && sample < 64) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    fun exists(path: String): Boolean {
        return path.isNotBlank() && File(path).exists()
    }
}
