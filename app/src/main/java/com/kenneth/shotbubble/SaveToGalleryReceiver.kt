package com.kenneth.shotbubble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlin.concurrent.thread

/** Handles the notification's "save to gallery" action. */
class SaveToGalleryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_FILE_NAME) ?: return
        val pending = goAsync()
        thread {
            val messageRes = try {
                val file = ScreenshotStore.find(context, name)
                if (file == null) {
                    R.string.toast_save_expired
                } else {
                    ScreenshotStore.copyToGallery(context, file)
                    R.string.toast_saved
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save to gallery", e)
                R.string.toast_save_failed
            }
            Handler(Looper.getMainLooper()).post {
                Notifier.cancel(context)
                Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_FILE_NAME = "file_name"
        private const val TAG = "ShotBubble"
    }
}
