package com.kenneth.shotbubble

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps screenshots in the app's private cache (never MediaStore), so they don't show up in
 * the gallery. Only the latest one is kept: it is the one the clipboard points to.
 */
object ScreenshotStore {
    private const val DIR = "shots"
    private const val KEEP = 1
    private const val THUMBNAIL_WIDTH = 540
    private val GALLERY_PATH = "${Environment.DIRECTORY_PICTURES}/Screenshots"

    fun save(context: Context, bitmap: Bitmap): File {
        val dir = shotsDir(context).apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

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
    fun copyToGallery(context: Context, file: File) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(file.lastModified()))
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Screenshot_$timestamp.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, GALLERY_PATH)
            put(MediaStore.Images.Media.DATE_TAKEN, file.lastModified())
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values,
        ) ?: error("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun shotsDir(context: Context) = File(context.cacheDir, DIR)
}
