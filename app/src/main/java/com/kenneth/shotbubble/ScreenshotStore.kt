package com.kenneth.shotbubble

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Keeps screenshots in the app's private cache (never MediaStore), so they don't show up in
 * the gallery. Only the latest one is kept: it is the one the clipboard points to.
 */
object ScreenshotStore {
    private const val DIR = "shots"
    private const val KEEP = 1

    fun save(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        dir.listFiles()
            ?.sortedByDescending { it.name }
            ?.drop(KEEP)
            ?.forEach { it.delete() }

        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
