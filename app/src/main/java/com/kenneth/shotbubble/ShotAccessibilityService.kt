package com.kenneth.shotbubble

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.quicksettings.TileService
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/**
 * Hosts the floating bubble and turns a tap into a screenshot that goes straight to the
 * clipboard. Uses AccessibilityService.takeScreenshot, so nothing is written to MediaStore.
 */
class ShotAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var bubble: BubbleView? = null
    private var capturing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        if (Prefs.isBubbleVisible(this)) showBubble()
        refreshTile()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Post so the new window metrics are in place before we re-clamp the bubble.
        mainHandler.post { bubble?.reposition() }
    }

    override fun onDestroy() {
        hideBubble()
        instanceRef = null
        worker.shutdown()
        refreshTile()
        super.onDestroy()
    }

    fun setBubbleVisible(visible: Boolean) {
        if (visible) showBubble() else hideBubble()
    }

    private fun showBubble() {
        if (bubble != null) return
        bubble = BubbleView(this, getSystemService(WindowManager::class.java), ::captureToClipboard)
            .also { it.attach() }
    }

    private fun hideBubble() {
        bubble?.detach()
        bubble = null
    }

    private fun captureToClipboard() {
        if (capturing) return
        capturing = true
        // Hide the bubble and wait for a frame so it doesn't end up in the screenshot.
        bubble?.visibility = View.INVISIBLE
        mainHandler.postDelayed(
            { takeScreenshot(Display.DEFAULT_DISPLAY, worker, screenshotCallback) },
            HIDE_DELAY_MS,
        )
    }

    private val screenshotCallback = object : TakeScreenshotCallback {
        // Runs on the worker thread.
        override fun onSuccess(result: ScreenshotResult) {
            mainHandler.post(::restoreBubble)
            val captured = try {
                val bitmap = result.hardwareBuffer.use {
                    Bitmap.wrapHardwareBuffer(it, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                } ?: error("wrapHardwareBuffer returned null")
                val file = ScreenshotStore.save(this@ShotAccessibilityService, bitmap)
                val thumbnail = ScreenshotStore.thumbnail(bitmap)
                bitmap.recycle()
                file to thumbnail
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save screenshot", e)
                null
            }
            mainHandler.post {
                if (captured != null) {
                    onCaptured(captured.first, captured.second)
                } else {
                    toast(getString(R.string.toast_failed, -1))
                }
            }
        }

        override fun onFailure(errorCode: Int) {
            Log.w(TAG, "takeScreenshot failed: $errorCode")
            mainHandler.post {
                restoreBubble()
                toast(
                    when (errorCode) {
                        ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> getString(R.string.toast_secure)
                        ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> getString(R.string.toast_too_fast)
                        else -> getString(R.string.toast_failed, errorCode)
                    }
                )
            }
        }
    }

    private fun restoreBubble() {
        bubble?.visibility = View.VISIBLE
        capturing = false
    }

    private fun onCaptured(file: File, thumbnail: Bitmap) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newUri(contentResolver, "Screenshot", ScreenshotStore.uriFor(this, file)))
        vibrate()
        Notifier.showCaptured(this, file, thumbnail)
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        // Notification usage so it still buzzes when system touch feedback is turned off.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build(),
            )
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun refreshTile() =
        TileService.requestListeningState(this, ComponentName(this, BubbleTileService::class.java))

    companion object {
        private const val TAG = "ShotBubble"
        private const val HIDE_DELAY_MS = 150L

        @Volatile
        private var instanceRef: WeakReference<ShotAccessibilityService>? = null

        /** The running service, or null when it isn't enabled in Accessibility settings. */
        val instance: ShotAccessibilityService? get() = instanceRef?.get()
    }
}
