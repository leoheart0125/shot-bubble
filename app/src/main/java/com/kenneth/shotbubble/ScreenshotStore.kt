package com.kenneth.shotbubble

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps clipboard screenshots in the app's private cache (never MediaStore), so they don't show
 * up in the gallery. Only the latest one is kept: it is the one the clipboard points to.
 *
 * Clipboard shots are JPEG at quality 100: on a Pixel 1080x2424 shot that encodes in ~40 ms
 * versus ~1 s for PNG, and the clipboard and notification wait on it. Gallery shots stay PNG.
 */
object ScreenshotStore {
    private const val DIR = "shots"
    private const val KEEP = 1
    private const val THUMBNAIL_WIDTH = 540
    private val GALLERY_PATH = "${Environment.DIRECTORY_PICTURES}/Screenshots"

    fun save(context: Context, bitmap: Bitmap): File {
        val dir = shotsDir(context).apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }

        dir.listFiles()
            ?.sortedByDescending { it.name }
            ?.drop(KEEP)
            ?.forEach { it.delete() }

        return file
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Looks up a cached screenshot by name; null if it has since been replaced by a newer one. */
    fun find(context: Context, name: String): File? {
        val dir = shotsDir(context)
        return File(dir, name).takeIf { it.parentFile == dir && it.isFile }
    }

    fun thumbnail(bitmap: Bitmap): Bitmap {
        val height = bitmap.height * THUMBNAIL_WIDTH / bitmap.width
        return Bitmap.createScaledBitmap(bitmap, THUMBNAIL_WIDTH, height, true)
    }

    /** Copies a cached screenshot into the gallery's Screenshots album, like a system screenshot. */
    fun copyToGallery(context: Context, file: File) =
        insertIntoGallery(context, file.lastModified(), jpeg = file.extension == "jpg") { out ->
            file.inputStream().use { it.copyTo(out) }
        }

    /** Writes a fresh screenshot straight into the gallery's Screenshots album, skipping the cache. */
    fun saveToGallery(context: Context, bitmap: Bitmap) =
        insertIntoGallery(context, System.currentTimeMillis(), jpeg = false) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

    private fun insertIntoGallery(context: Context, takenAt: Long, jpeg: Boolean, write: (OutputStream) -> Unit) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(takenAt))
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Screenshot_$timestamp.${if (jpeg) "jpg" else "png"}")
            put(MediaStore.Images.Media.MIME_TYPE, if (jpeg) "image/jpeg" else "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, GALLERY_PATH)
            put(MediaStore.Images.Media.DATE_TAKEN, takenAt)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values,
        ) ?: error("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)!!.use(write)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun shotsDir(context: Context) = File(context.cacheDir, DIR)
}
