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
 * Hosts the floating bubble. Tapping it opens a menu: copy a screenshot straight to the clipboard
 * (nothing written to MediaStore), or save one to the gallery like a normal screenshot.
 * Uses AccessibilityService.takeScreenshot for both.
 */
class ShotAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var bubble: BubbleView? = null
    private var menu: BubbleMenu? = null
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
        // The menu was placed for the old screen size; close it rather than re-layout.
        menu?.dismiss()
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
        val windowManager = getSystemService(WindowManager::class.java)
        bubble = BubbleView(
            this,
            windowManager,
            onTap = ::openMenu,
            // Only the bubble has to disappear here (no menu window), so a shorter wait is enough.
            onQuickCapture = { capture(Prefs.quickCaptureMode(this), QUICK_HIDE_DELAY_MS) },
        ).also { it.attach() }
        menu = BubbleMenu(this, windowManager, onDismiss = { bubble?.scheduleDim() })
    }

    private fun hideBubble() {
        menu?.dismiss()
        menu = null
        bubble?.detach()
        bubble = null
    }

    private fun openMenu() {
        val bubble = bubble ?: return
        val menu = menu ?: return
        if (capturing || menu.isShowing) return
        bubble.wake()
        val items = MenuAction.entries.associateWith { action ->
            BubbleMenu.Item(action.icon, action.label) {
                when (action) {
                    MenuAction.COPY -> capture(CaptureMode.CLIPBOARD)
                    MenuAction.GALLERY -> capture(CaptureMode.GALLERY)
                    MenuAction.HIDE -> hideFromMenu()
                    MenuAction.BACK -> Unit
                }
            }
        }
        // Slots are top-left, top-right, bottom-left, bottom-right, as arranged in the app.
        val slots = Prefs.menuSlots(this).map(items::getValue)
        menu.show(bubble, rows = slots.chunked(2), primary = items.getValue(MenuAction.COPY))
    }

    /** Same as turning the Quick Settings tile off; the tile or the app's main screen brings it back. */
    private fun hideFromMenu() {
        Prefs.setBubbleVisible(this, false)
        hideBubble()
        refreshTile()
        toast(getString(R.string.toast_hidden))
    }

    private fun capture(mode: CaptureMode, hideDelayMs: Long = MENU_HIDE_DELAY_MS) {
        if (capturing) return
        capturing = true
        // Hide the bubble and wait for the compositor so neither it nor the menu ends up in the screenshot.
        bubble?.visibility = View.INVISIBLE
        mainHandler.postDelayed(
            { takeScreenshot(Display.DEFAULT_DISPLAY, worker, ScreenshotCallback(mode)) },
            hideDelayMs,
        )
    }

    private inner class ScreenshotCallback(private val mode: CaptureMode) : TakeScreenshotCallback {
        // Runs on the worker thread.
        override fun onSuccess(result: ScreenshotResult) {
            // Buzz as soon as the shot is taken, before the encode/save below.
            mainHandler.post {
                restoreBubble()
                vibrate()
            }
            val onDone: () -> Unit = try {
                val bitmap = result.hardwareBuffer.use {
                    Bitmap.wrapHardwareBuffer(it, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                } ?: error("wrapHardwareBuffer returned null")
                try {
                    when (mode) {
                        CaptureMode.CLIPBOARD -> {
                            val file = ScreenshotStore.save(this@ShotAccessibilityService, bitmap)
                            val thumbnail = ScreenshotStore.thumbnail(bitmap)
                            ({ onCopied(file, thumbnail) })
                        }
                        CaptureMode.GALLERY -> {
                            ScreenshotStore.saveToGallery(this@ShotAccessibilityService, bitmap)
                            ::onSavedToGallery
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save screenshot", e)
                ({ toast(getString(R.string.toast_failed, -1)) })
            }
            mainHandler.post(onDone)
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

    private fun onCopied(file: File, thumbnail: Bitmap) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newUri(contentResolver, "Screenshot", ScreenshotStore.uriFor(this, file)))
        Notifier.showCaptured(this, file, thumbnail)
    }

    private fun onSavedToGallery() {
        toast(getString(R.string.toast_saved))
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
        /** After a menu choice: the menu window has to be removed as well as the bubble hidden. */
        private const val MENU_HIDE_DELAY_MS = 200L
        private const val QUICK_HIDE_DELAY_MS = 50L

        @Volatile
        private var instanceRef: WeakReference<ShotAccessibilityService>? = null

        /** The running service, or null when it isn't enabled in Accessibility settings. */
        val instance: ShotAccessibilityService? get() = instanceRef?.get()
    }
}
